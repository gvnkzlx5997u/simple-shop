@echo off
rem ============================================================
rem  Start BACKEND against the TEST database (simple_shop_test)
rem  NOTE: keep this file ASCII-only (cmd reads .bat as GBK).
rem  DB_URL / DB_PASSWORD are read by application.yml:
rem      url:      ${DB_URL:...}
rem      password: ${DB_PASSWORD:...}
rem  Using  set "VAR=value"  keeps the '&' inside the URL safe.
rem ============================================================
cd /d "%~dp0"

set "DB_URL=jdbc:mysql://127.0.0.1:3306/simple_shop_test?useUnicode=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci&connectionTimeZone=UTC&rewriteBatchedStatements=true"
call "%~dp0local-config.bat"
if not defined E2E_DB_PASSWORD goto nolocal
set "DB_PASSWORD=%E2E_DB_PASSWORD%"
set "SERVER__PORT="
set "SERVER_PORT="

rem  The backend war lives in the SIBLING folder "simple-shop-backend",
rem  relative to this file - never hardcode an absolute path.
set "WAR=%~dp0..\simple-shop-backend\target\simple-shop.war"
if not exist "%WAR%" goto nowar

echo [BACKEND] port 8080  -  database = simple_shop_test  [TEST db]
echo [BACKEND] close this window to stop the backend.
echo.

rem  NOTE 4: --simple-shop.rate-limit.submit-intention-per-minute=1000
rem          raises the submit rate limit FOR THIS TEST BACKEND ONLY.
rem          Why: the product default is 10 per minute per IP (NFR-17).
rem          One full acceptance run submits 6 intentions in about 15
rem          seconds, so running the suite TWICE within one minute would
rem          hit the limit and make test D0 fail with a "please retry later" message.
rem          The product config in application.yml is NOT changed - the
rem          real database / real deployment still uses 10 per minute.
java -Dserver.port=8080 -jar "%WAR%" --simple-shop.rate-limit.submit-intention-per-minute=1000

echo.
echo [BACKEND] stopped. Press any key to close.
pause > nul

goto :eof

:nolocal
echo [ERROR] local-config.bat is missing or empty.
echo         Copy local-config.example.bat to local-config.bat
echo         and fill in the real passwords, then run again.
echo.
pause > nul
exit /b 1

:nowar
echo [ERROR] Backend war not found:
echo         ..\simple-shop-backend\target\simple-shop.war
echo.
echo         Build the backend first, then run this file again.
echo         (this folder must stay inside the simple-shop repository)
echo.
pause > nul
exit /b 1
