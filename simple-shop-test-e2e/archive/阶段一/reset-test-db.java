/*
 * ============================================================================
 *  reset-test-db.java - reset the E2E TEST database over JDBC.
 *
 *  WHY THIS FILE EXISTS
 *    Step 2 of the acceptance run used to reset the database by calling a
 *    locally installed MySQL client:
 *        "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" ...
 *    On this machine MySQL runs inside a Docker container and no client is
 *    installed on Windows, so the reset step always failed and the whole
 *    acceptance suite could not start.
 *
 *    This helper removes that dependency: it connects with JDBC through
 *    exactly the same DB_URL that run-backend.bat hands to the backend and
 *    then executes reset-test-db.sql statement by statement.
 *    No MySQL client, no Docker CLI, no hardcoded absolute path.
 *
 *  USAGE
 *    java reset-test-db.java            (run from this folder, exit 0 = OK)
 *
 *  DB_URL RESOLUTION ORDER
 *    1. environment variable E2E_DB_URL
 *    2. the  set "DB_URL=..."  line inside run-backend.bat   <- single source
 *       of truth, so the reset always targets the database the backend uses.
 *
 *  OTHER ENVIRONMENT VARIABLES
 *    E2E_DB_PASSWORD   root password                        (required)
 *    E2E_DB_USER       user name                            (default root)
 *    E2E_DB_NAME       expected test database name          (default
 *                                                            simple_shop_test)
 *    E2E_JDBC_DRIVER   explicit mysql-connector-j jar path  (auto otherwise)
 *
 *  SAFETY
 *    Real data must never be lost, so this helper refuses to run unless the
 *    database inside DB_URL is exactly E2E_DB_NAME - and it always refuses
 *    the production database simple_shop.
 *
 *  NOTE: keep this file ASCII-only, like the .bat files next to it.
 * ============================================================================
 */

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

class ResetTestDb {

    private static final String DEFAULT_DB_NAME = "simple_shop_test";
    private static final String PRODUCTION_DB_NAME = "simple_shop";

    /** Business tables emptied by reset-test-db.sql (seed tables are kept). */
    private static final String[] EMPTIED_TABLES = {
        "simpleshop_intentions",
        "simpleshop_intentions_history",
        "simpleshop_goods",
        "simpleshop_goods_history",
        "simpleshop_trade_history"
    };

    public static void main(String[] args) {
        try {
            // 2-run tests.bat does  cd /d "%~dp0"  before calling us.
            Path here = Paths.get("").toAbsolutePath();

            String url = resolveUrl(here);
            String user = envOr("E2E_DB_USER", "root");
            String password = System.getenv("E2E_DB_PASSWORD");
            if (password == null || password.isEmpty()) {
                fail("E2E_DB_PASSWORD is not set."
                     + " Start this helper through the step 2 script, or set it yourself.");
            }

            String dbName = databaseName(url);
            String expected = envOr("E2E_DB_NAME", DEFAULT_DB_NAME);
            if (PRODUCTION_DB_NAME.equals(dbName) || !expected.equals(dbName)) {
                fail("refusing to reset database '" + dbName + "'."
                     + " This helper only ever resets '" + expected + "'.");
            }

            Path sqlFile = here.resolve("reset-test-db.sql");
            if (!Files.isRegularFile(sqlFile)) {
                fail("SQL file not found: " + sqlFile);
            }
            List<String> statements = readStatements(sqlFile);

            Path driverJar = findDriverJar(here);

            System.out.println("  [DB] url      : " + url);
            System.out.println("  [DB] database : " + dbName);
            System.out.println("  [DB] driver   : " + driverJar);
            System.out.println("  [DB] sql      : " + sqlFile.getFileName()
                               + "  (" + statements.size() + " statements)");

            try (Connection connection = connect(driverJar, url, user, password)) {
                try (Statement statement = connection.createStatement()) {
                    for (String sql : statements) {
                        statement.execute(sql);
                    }
                }
                verify(connection);
            }
            System.out.println("  [DB] reset OK.");
        } catch (Exception e) {
            fail(describe(e)
                 + "\n  [DB][ERROR] Is the MySQL container running, and is DB_URL reachable"
                 + " from Windows?");
        }
    }

    // ------------------------------------------------------------------ url --

    /** E2E_DB_URL wins; otherwise the DB_URL line of run-backend.bat is reused. */
    private static String resolveUrl(Path here) throws IOException {
        String fromEnv = System.getenv("E2E_DB_URL");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }
        Path runBackend = here.resolve("run-backend.bat");
        if (Files.isRegularFile(runBackend)) {
            String text = Files.readString(runBackend, StandardCharsets.UTF_8);
            Matcher matcher = Pattern
                .compile("^[ \t]*set[ \t]+\"DB_URL=([^\"]+)\"",
                         Pattern.CASE_INSENSITIVE | Pattern.MULTILINE)
                .matcher(text);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        }
        fail("cannot find DB_URL: run-backend.bat has no  set \"DB_URL=...\"  line"
             + " and E2E_DB_URL is not set.");
        return null;
    }

    /** Reads the database name out of  jdbc:mysql://host:port/NAME?params . */
    private static String databaseName(String url) {
        String rest = url;
        int question = rest.indexOf('?');
        if (question >= 0) {
            rest = rest.substring(0, question);
        }
        int slash = rest.lastIndexOf('/');
        if (slash < 0) {
            fail("cannot read the database name from DB_URL: " + url);
        }
        String name = rest.substring(slash + 1).trim();
        if (name.isEmpty()) {
            fail("DB_URL does not name a database: " + url);
        }
        return name;
    }

    // ----------------------------------------------------------- sql script --

    /**
     * Splits reset-test-db.sql into executable statements.
     * Blank lines and  --  comment lines are dropped, so the Chinese comments
     * in the script never reach the server.
     */
    private static List<String> readStatements(Path sqlFile) throws IOException {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String rawLine : Files.readAllLines(sqlFile, StandardCharsets.UTF_8)) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("--")) {
                continue;
            }
            current.append(line).append('\n');
            int semicolon;
            while ((semicolon = current.indexOf(";")) >= 0) {
                String statement = current.substring(0, semicolon).trim();
                current.delete(0, semicolon + 1);
                if (!statement.isEmpty()) {
                    statements.add(statement);
                }
            }
        }
        String tail = current.toString().trim();
        if (!tail.isEmpty()) {
            statements.add(tail);
        }
        return statements;
    }

    // --------------------------------------------------------------- driver --

    /**
     * Finds a mysql driver without asking the caller for a classpath:
     * first the copy the backend itself ships inside its WAR, then the local
     * Maven repository.
     */
    private static Path findDriverJar(Path here) throws IOException {
        String explicit = System.getenv("E2E_JDBC_DRIVER");
        if (explicit != null && !explicit.isBlank()) {
            Path path = Paths.get(explicit.trim());
            if (!Files.isRegularFile(path)) {
                fail("E2E_JDBC_DRIVER points to a missing file: " + path);
            }
            return path;
        }

        Path war = here.resolve("..").resolve("simple-shop-backend")
                       .resolve("target").resolve("simple-shop.war").normalize();
        if (Files.isRegularFile(war)) {
            Path fromWar = extractDriverFromWar(war);
            if (fromWar != null) {
                return fromWar;
            }
        }

        Path repo = Paths.get(System.getProperty("user.home"),
                              ".m2", "repository", "com", "mysql", "mysql-connector-j");
        if (Files.isDirectory(repo)) {
            try (Stream<Path> walk = Files.walk(repo, 3)) {
                Optional<Path> newest = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> isDriverJar(p.getFileName().toString()))
                    .max(Comparator.comparingLong(p -> p.toFile().lastModified()));
                if (newest.isPresent()) {
                    return newest.get();
                }
            }
        }

        fail("no MySQL JDBC driver found."
             + " Build the backend first, or point E2E_JDBC_DRIVER at"
             + " mysql-connector-j-*.jar.");
        return null;
    }

    private static boolean isDriverJar(String fileName) {
        String name = fileName.toLowerCase();
        return name.startsWith("mysql-connector-j")
            && name.endsWith(".jar")
            && !name.endsWith("-sources.jar")
            && !name.endsWith("-javadoc.jar");
    }

    /** Copies WEB-INF/lib/mysql-connector-j-*.jar out of the backend WAR. */
    private static Path extractDriverFromWar(Path war) throws IOException {
        try (ZipFile zip = new ZipFile(war.toFile())) {
            ZipEntry entry = zip.stream()
                .filter(e -> !e.isDirectory())
                .filter(e -> e.getName().startsWith("WEB-INF/lib/"))
                .filter(e -> isDriverJar(e.getName().substring(e.getName().lastIndexOf('/') + 1)))
                .findFirst()
                .orElse(null);
            if (entry == null) {
                return null;
            }
            Path target = Files.createTempDirectory("simple-shop-jdbc-")
                               .resolve("mysql-connector-j.jar");
            try (InputStream in = zip.getInputStream(entry)) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            target.toFile().deleteOnExit();
            return target;
        }
    }

    // ------------------------------------------------------------ jdbc work --

    /**
     * Connects through the driver jar explicitly, because DriverManager only
     * accepts drivers that are visible to the caller's own class loader.
     */
    private static Connection connect(Path driverJar, String url, String user, String password)
            throws Exception {
        URL[] urls = { driverJar.toUri().toURL() };
        URLClassLoader loader = new URLClassLoader(urls, ResetTestDb.class.getClassLoader());
        Thread.currentThread().setContextClassLoader(loader);

        Driver driver;
        try {
            driver = newDriver("com.mysql.cj.jdbc.Driver", loader);
        } catch (ClassNotFoundException modern) {
            driver = newDriver("com.mysql.jdbc.Driver", loader);
        }

        Properties properties = new Properties();
        properties.setProperty("user", user);
        properties.setProperty("password", password);
        Connection connection = driver.connect(url, properties);
        if (connection == null) {
            throw new IllegalStateException("the MySQL driver rejected DB_URL: " + url);
        }
        return connection;
    }

    private static Driver newDriver(String className, ClassLoader loader) throws Exception {
        Object instance = Class.forName(className, true, loader)
                               .getDeclaredConstructor().newInstance();
        return (Driver) instance;
    }

    /** Proves the reset really happened instead of trusting the exit code. */
    private static void verify(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            for (String table : EMPTIED_TABLES) {
                try (ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
                    rs.next();
                    long rows = rs.getLong(1);
                    if (rows != 0) {
                        fail("table " + table + " still holds " + rows
                             + " row(s) after the reset.");
                    }
                }
            }
            try (ResultSet rs = statement.executeQuery(
                    "SELECT current_value FROM simpleshop_queue_sequence WHERE id = 1")) {
                if (!rs.next()) {
                    fail("queue sequence row id=1 is missing;"
                         + " V2__seed_initial_data.sql must provide it.");
                }
                long value = rs.getLong(1);
                if (value != 0) {
                    fail("queue sequence was not reset to 0, current_value=" + value + ".");
                }
            }
        }
    }

    // --------------------------------------------------------------- output --

    private static String envOr(String name, String fallback) {
        String value = System.getenv(name);
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }

    private static String describe(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return e.getClass().getSimpleName();
        }
        return e.getClass().getSimpleName() + ": " + message;
    }

    private static void fail(String message) {
        System.out.println("  [DB][ERROR] " + message);
        System.exit(1);
    }
}
