@echo off
rem ============================================================
rem  STEP 2 - reset TEST db + run all acceptance tests
rem  NOTE 1: keep this file ASCII-only (cmd reads .bat as GBK).
rem  NOTE 2: keep CRLF line endings.
rem  NOTE 3: never put brackets or pipe or ampersand inside an echo
rem          that sits in a parenthesised if-block, because cmd
rem          mis-parses the block.  That is why this file uses goto
rem          instead of the if-block form.
rem ============================================================
cd /d "%~dp0"
title simple-shop E2E - run tests

call "%~dp0local-config.bat"
if not defined E2E_DB_PASSWORD goto nolocal
set "SELLER_PASSWORD=%E2E_SELLER_PASSWORD%"
if not defined SELLER_PASSWORD goto nolocal

echo ==================================================
echo   STEP 1/2   reset TEST database - simple_shop_test
echo ==================================================
"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -uroot -p%E2E_DB_PASSWORD% -h127.0.0.1 simple_shop_test < "%~dp0reset-test-db.sql" 2>nul
if errorlevel 1 goto reset_failed
echo   TEST database reset OK. Real database is untouched.
echo.

echo ==================================================
echo   STEP 2/2   run acceptance tests
echo ==================================================
echo   A browser window will open and click through the
echo   site automatically. Do not close it.
echo.
call npx playwright test
if errorlevel 1 goto tests_failed

echo.
echo  ==============================================
echo    ALL TESTS PASSED
echo  ==============================================
echo.
echo  To view the HTML report, open the 3rd file.
echo.
pause
exit /b 0

:reset_failed
echo.
echo  [ERROR] Reset failed.
echo          Is the MySQL80 service running?
echo          Did you open the 1st file first?
echo.
pause
exit /b 1

:tests_failed
echo.
echo  ==============================================
echo    SOME TESTS FAILED
echo    Screenshots and traces are in the evidence folder.
echo  ==============================================
echo.
echo  To see what failed, open the 3rd file.
echo.
pause
exit /b 1

:nolocal
echo.
echo  [ERROR] local-config.bat is missing or empty.
echo          Copy local-config.example.bat to local-config.bat
echo          and fill in the real passwords, then run again.
echo.
pause
exit /b 1
