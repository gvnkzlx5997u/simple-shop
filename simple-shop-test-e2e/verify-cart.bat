@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

echo ============================================
echo  D3 cart end-to-end verification
echo ============================================
echo.

rem 配置（数据库口令 / 夹具库 / 地址）全部来自项目根的 .env，模板见 .env.example。
rem 本脚本只做两件事：检查服务在不在、然后跑 node 脚本。

echo [1/2] checking services...
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

echo [2/2] running verification...
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
