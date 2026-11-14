@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

echo ============================================
echo  D3 cart end-to-end verification
echo ============================================
echo.

echo [1/3] checking services...
netstat -ano | findstr ":8080 " | findstr LISTENING >nul
if errorlevel 1 (
  echo    FAIL: backend not listening on 8080
  echo    start it first ^(simple-shop-backend: mvn spring-boot:run^)
  goto :fail
)
netstat -ano | findstr ":5174 " | findstr LISTENING >nul
if errorlevel 1 (
  echo    FAIL: frontend not listening on 5174
  echo    start it first ^(simple-shop-buyer-frontend: npm run dev^)
  goto :fail
)
echo    OK: 8080 and 5174 are both up
echo.

echo [2/3] loading database password from local-config.bat...
if not exist local-config.bat (
  echo    FAIL: local-config.bat not found
  echo    copy archive\local-config.example.bat to local-config.bat and fill in the password
  goto :fail
)
call local-config.bat
set "DB_PASSWORD=%E2E_DB_PASSWORD%"
if "%DB_PASSWORD%"=="" (
  echo    FAIL: E2E_DB_PASSWORD is empty in local-config.bat
  goto :fail
)
echo    OK: password loaded ^(not printed^)
echo.

echo [3/3] running verification...
echo.
node d3-cart-check.js
set "RC=%ERRORLEVEL%"

echo.
echo ============================================
if "%RC%"=="0" (
  echo  RESULT: ALL CHECKS PASSED
) else (
  echo  RESULT: FAILED  ^(exit code %RC%^)
)
echo ============================================
echo.
pause
exit /b %RC%

:fail
echo.
pause
exit /b 1
