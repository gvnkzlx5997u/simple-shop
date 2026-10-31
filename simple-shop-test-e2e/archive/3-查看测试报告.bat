@echo off
rem ============================================================
rem  STEP 3 - open the HTML test report (double-click me)
rem  NOTE 1: keep this file ASCII-only (cmd reads .bat as GBK).
rem  NOTE 2: keep CRLF line endings.
rem  NOTE 3: our report folder is "report" (set in playwright.config.ts),
rem          NOT the playwright default "playwright-report". If the folder
rem          name is not passed explicitly, show-report prints
rem          "No report found at ...\playwright-report" and exits at once,
rem          so the window flashes and nothing opens.
rem ============================================================
cd /d "%~dp0"
title simple-shop E2E - report

if not exist "report\index.html" goto noreport

echo ==================================================
echo   Opening the HTML test report ...
echo ==================================================
echo   URL:  http://localhost:9323
echo.
echo   A browser window should open by itself.
echo   If it does not, open the URL above by hand.
echo.
echo   KEEP THIS WINDOW OPEN while you read the report.
echo   Close it (or press Ctrl+C) to stop the report server.
echo.
call npx playwright show-report report
echo.
echo Report server stopped.
pause
exit /b 0

:noreport
echo.
echo  [ERROR] No report found in the "report" folder.
echo          Run the 2nd file first to generate the report.
echo.
pause
exit /b 1
