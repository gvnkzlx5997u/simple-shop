@echo off
rem ============================================================
rem  Start SELLER frontend (vite dev server) on port 5173
rem  NOTE: keep this file ASCII-only (cmd reads .bat as GBK).
rem ============================================================
cd /d "%~dp0"

rem  The seller frontend lives in the SIBLING folder "simple-shop-frontend".
set "FE=%~dp0..\simple-shop-frontend"
if not exist "%FE%\package.json" goto nofront
cd /d "%FE%"
echo [FRONTEND] starting vite dev server on port 5173
echo [FRONTEND] close this window to stop the frontend.
echo.
call npm run dev
echo.
echo [FRONTEND] stopped. Press any key to close.
pause > nul

goto :eof

:nofront
echo [ERROR] Seller frontend not found:
echo         ..\simple-shop-frontend
echo.
echo         Is this folder still inside the simple-shop repository?
echo.
pause > nul
exit /b 1
