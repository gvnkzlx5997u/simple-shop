/**
 * E2E 的 MySQL 访问层：node 直连（mysql2），不再调 mysql / mysqlsh 命令行。
 *
 * 为什么不再用命令行：
 *   1. 依赖安装路径与 PATH —— 本机只装了 MySQL Shell / Workbench，没有 mysql.exe，
 *      `reset-e2e-db.bat` 里写死的 `C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe` 根本不存在；
 *   2. 传 SQL 要过 cmd.exe：多行 SQL 会被当多条命令只执行第一段，引号要自己转义，
 *      中文要额外加 `--default-character-set=utf8mb4`；
 *   3. 失败只能从 stderr 文本里猜，退出码还混着告警，没法结构化判断。
 *
 * 取值口径**刻意对齐 `mysql -N -B`（批处理模式）**：无表头、单值、一律字符串。
 * 这样既有的对账断言（`=== '1'`、`=== '230.00'`）不用跟着改。
 *
 * 配置来自项目根的 `.env`（模板 `.env.example`），由 loadDotEnv() 在模块加载时读入；
 * 不需要在终端里 export，也不需要 .bat 里 set。
 *
 * 只给「重建库 + 对账脚本」用。Playwright 用例本身仍然只走接口与 UI：
 * 造数走接口、断言看接口返回，是这套 E2E 一直守着的边界；把业务断言塞进 SQL
 * 会让用例跟着表结构漂移，改一次迁移就要改一遍用例。
 */
const fs = require('node:fs');
const path = require('node:path');
const mysql = require('mysql2/promise');

/**
 * 读项目根的 `.env`（node 自带解析，不需要 dotenv 依赖）。
 *
 * `process.loadEnvFile` 不会覆盖已经存在的环境变量，所以单项配置的优先级天然是
 * 「真实环境变量 > .env > 代码默认值」。缺 .env 时静默跳过，只靠环境变量也能跑。
 *
 * 但要先记下「加载 .env 之前就存在哪些键」：同一项配置有别名时
 * （`E2E_DB_NAME` 与旧的 `DB_NAME`），不能按别名顺序谁先命中就用谁——
 * 否则 .env 里的 E2E_DB_NAME 会盖掉终端里刚设的 DB_NAME，与上面的优先级承诺相反。
 */
const SHELL_ENV_KEYS = new Set(Object.keys(process.env));

function loadDotEnv() {
  const file = path.join(__dirname, '..', '.env');
  if (typeof process.loadEnvFile !== 'function' || !fs.existsSync(file)) {
    return false;
  }
  process.loadEnvFile(file);
  return true;
}

loadDotEnv();

const DEFAULT = {
  host: '127.0.0.1',
  port: 3306,
  user: 'root',
  database: 'simple_shop_e2e',
};

/** 重置脚本绝不能碰的库：开发库、测试库、系统库。 */
const PROTECTED_DATABASES = new Set([
  'simple_shop',
  'simple_shop_test',
  'mysql',
  'sys',
  'information_schema',
  'performance_schema',
]);

/**
 * 按别名顺序取第一个有值的配置。
 * 先扫「终端里确实设过的」，再扫「.env 补进来的」，保证终端覆盖优先于文件。
 */
function pick(...names) {
  for (const name of names) {
    const value = process.env[name];
    if (value !== undefined && value !== '' && SHELL_ENV_KEYS.has(name)) {
      return value;
    }
  }
  for (const name of names) {
    const value = process.env[name];
    if (value !== undefined && value !== '') {
      return value;
    }
  }
  return undefined;
}

/**
 * 连接参数：环境变量优先，`overrides` 再次之（脚本里显式指定库名时用）。
 * `overrides.database === null` 表示「连服务器但不选库」，建库/删库时用。
 */
function resolve(overrides = {}) {
  const database =
    overrides.database === null
      ? null
      : (overrides.database ?? pick('E2E_DB_NAME', 'DB_NAME') ?? DEFAULT.database);
  return {
    host: overrides.host ?? pick('E2E_DB_HOST', 'DB_HOST') ?? DEFAULT.host,
    port: Number(overrides.port ?? pick('E2E_DB_PORT', 'DB_PORT') ?? DEFAULT.port),
    user: overrides.user ?? pick('E2E_DB_USER', 'DB_USER') ?? DEFAULT.user,
    password: overrides.password ?? pick('E2E_DB_PASSWORD', 'DB_PASSWORD') ?? '',
    database,
  };
}

/** 不含口令的目标描述，用来打日志。 */
function describe(config) {
  return `${config.user}@${config.host}:${config.port}/${config.database ?? '(无库)'}`;
}

function connectionOptions(config) {
  return {
    host: config.host,
    port: config.port,
    user: config.user,
    password: config.password,
    ...(config.database ? { database: config.database } : {}),
    // 与 reset 脚本建库时的 collation 一致，中文比较不会被换成别的排序规则。
    charset: 'utf8mb4_unicode_ci',
    // DATETIME 按字符串返回，避免驱动做时区换算（后端一律存 UTC，对账要看到原始值）。
    dateStrings: true,
    // COUNT(*) 这类 BIGINT 也按字符串返回，口径同 CLI。
    supportBigNumbers: true,
    bigNumberStrings: true,
    multipleStatements: false,
    connectTimeout: 10_000,
  };
}

function reasonOf(error) {
  switch (error.code) {
    case 'ECONNREFUSED':
      return '端口没人监听，MySQL 没启动或端口不对（DB_HOST / DB_PORT）';
    case 'ER_ACCESS_DENIED_ERROR':
      return '账号或口令不对（E2E_DB_USER / E2E_DB_PASSWORD）';
    case 'ER_BAD_DB_ERROR':
      return '库不存在：检查 .env 里的 E2E_DB_NAME（对账脚本是 E2E_FIXTURE_DB_NAME），或先跑 npm run db:reset';
    case 'ETIMEDOUT':
      return '连接超时，检查主机与防火墙';
    default:
      return error.message;
  }
}

/** 带上下文的异常：一眼能看出连的哪个库、跑的哪句 SQL。 */
function failure(error, config, sql) {
  const statement = String(sql ?? '')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, 200);
  const wrapped = new Error(
    `[E2E/DB] ${describe(config)} 执行失败：${reasonOf(error)}` +
      (statement ? `\n  SQL: ${statement}` : '') +
      (config.password
        ? ''
        : '\n  提示：在项目根的 .env 里填 E2E_DB_PASSWORD（模板 .env.example）'),
  );
  wrapped.code = error.code;
  return wrapped;
}

let pool = null;
let poolKey = '';

/** 懒建连接池；同一进程换库时重建（E2E 脚本一次只连一个库）。 */
function getPool(overrides) {
  const config = resolve(overrides);
  const key = JSON.stringify([config.host, config.port, config.user, config.database]);
  if (pool && poolKey !== key) {
    const stale = pool;
    pool = null;
    void stale.end().catch(() => {});
  }
  if (!pool) {
    pool = mysql.createPool({ ...connectionOptions(config), connectionLimit: 2, waitForConnections: true });
    poolKey = key;
  }
  return { pool, config };
}

/** 关闭连接池；脚本结束前调用，避免 node 挂着等连接回收。 */
async function close() {
  if (!pool) return;
  const current = pool;
  pool = null;
  poolKey = '';
  await current.end().catch(() => {});
}

/**
 * 查询并返回行数组（`mysql -N -B` 去掉表头后的那部分）。
 * 只用于 SELECT；写操作用 run()。
 */
async function rows(sql, params = [], overrides) {
  const { pool: active, config } = getPool(overrides);
  try {
    const [result] = await active.query(sql, params);
    if (!Array.isArray(result)) {
      throw new Error('rows() 只接受 SELECT；写操作用 run()');
    }
    return result;
  } catch (error) {
    throw failure(error, config, sql);
  }
}

/** 单个值转字符串，口径同 CLI：NULL → 空串，数字/日期都转文本。 */
function toCell(value) {
  if (value === null || value === undefined) return '';
  return String(value);
}

/**
 * 取第一行第一列，**字符串**（CLI `-N -B` 的输出口径）。
 * 一行都没有时返回空串，与 CLI 输出为空一致。
 */
async function scalar(sql, params = [], overrides) {
  const result = await rows(sql, params, overrides);
  if (result.length === 0) return '';
  return toCell(Object.values(result[0])[0]);
}

/** 取第一行对象；没有行时返回 null。 */
async function one(sql, params = [], overrides) {
  const result = await rows(sql, params, overrides);
  return result.length > 0 ? result[0] : null;
}

/** 写操作：返回 mysql2 的 OkPacket（affectedRows / insertId）。 */
async function run(sql, params = [], overrides) {
  const { pool: active, config } = getPool(overrides);
  try {
    const [result] = await active.query(sql, params);
    return result;
  } catch (error) {
    throw failure(error, config, sql);
  }
}

/** 探活：连得上就返回版本号，连不上返回原因（不抛异常，供脚本先打一条清晰提示）。 */
async function probe(overrides) {
  const config = resolve(overrides);
  let connection = null;
  try {
    connection = await mysql.createConnection(connectionOptions(config));
    const [result] = await connection.query('SELECT VERSION() AS version');
    return { ok: true, target: describe(config), version: result[0].version };
  } catch (error) {
    return { ok: false, target: describe(config), reason: reasonOf(error), code: error.code };
  } finally {
    if (connection) await connection.end().catch(() => {});
  }
}

function assertSafeName(name) {
  if (!/^[A-Za-z0-9_]+$/.test(name)) {
    throw new Error(`[E2E/DB] 库名只允许字母、数字与下划线：${name}`);
  }
  if (PROTECTED_DATABASES.has(name)) {
    throw new Error(`[E2E/DB] 拒绝重建受保护的库 ${name}（只允许 *_e2e 之类的独立库）`);
  }
}

/**
 * 重建空库（DROP + CREATE）。表结构与种子数据由后端启动时的 Flyway 建。
 * 只传库名：连接时不选库，避免 DROP 到自己正连着的那个库上。
 */
async function recreateDatabase(name, overrides = {}) {
  assertSafeName(name);
  const config = resolve({ ...overrides, database: null });
  let connection = null;
  try {
    connection = await mysql.createConnection(connectionOptions(config));
    await connection.query(`DROP DATABASE IF EXISTS \`${name}\``);
    await connection.query(
      `CREATE DATABASE \`${name}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci`,
    );
    return { target: `${config.user}@${config.host}:${config.port}/${name}` };
  } catch (error) {
    throw failure(error, config, `DROP/CREATE DATABASE ${name}`);
  } finally {
    if (connection) await connection.end().catch(() => {});
  }
}

module.exports = {
  describe,
  resolve,
  probe,
  rows,
  scalar,
  one,
  run,
  close,
  recreateDatabase,
  /** 脚本里要拼 information_schema 查询时用当前库名。 */
  get database() {
    return resolve().database;
  },
};
