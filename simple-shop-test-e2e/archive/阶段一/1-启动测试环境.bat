@echo off
rem ============================================================
rem  STEP 1 - start the E2E test environment (double-click me)
rem  Opens TWO windows: backend (TEST db) + seller frontend.
rem  NOTE: keep this file ASCII-only (cmd reads .bat as GBK).
rem ============================================================
cd /d "%~dp0"
title simple-shop E2E - start environment

echo ==================================================
echo   simple-shop  E2E acceptance test environment
echo ==================================================
echo   Buyer  (mobile) : http://localhost:8080/
echo   Seller (desktop): http://localhost:5173/
echo --------------------------------------------------
echo   Backend uses the TEST database  simple_shop_test
echo   Your real database simple_shop is NOT touched.
echo ==================================================
echo.

echo Starting backend and frontend in two new windows...
start "simple-shop BACKEND (TEST db)" "%~dp0run-backend.bat"
echo   ... backend starting (needs about 10 seconds)
timeout /t 4 /nobreak >nul
start "simple-shop FRONTEND (5173)" "%~dp0run-frontend.bat"
echo   ... frontend starting (needs about 5 seconds)
echo.
echo ==================================================
echo   Wait until BOTH new windows report they started,
echo   then double-click the 2nd file:  "2-..."  (Step 2)
echo ==================================================
echo.
pause
