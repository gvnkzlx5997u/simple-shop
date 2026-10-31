@echo off
rem Recreate the dedicated E2E database (schema is rebuilt by Flyway on backend start).
rem Usage: set DB_PASSWORD=your_root_password  then run this file.
setlocal

if "%DB_PASSWORD%"=="" (
  echo [E2E] DB_PASSWORD is not set. Set it first: set DB_PASSWORD=...
  exit /b 1
)

set MYSQL_EXE=C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe
if not exist "%MYSQL_EXE%" (
  echo [E2E] mysql.exe not found at "%MYSQL_EXE%". Edit this file.
  exit /b 1
)

echo [E2E] dropping and recreating database simple_shop_e2e ...
"%MYSQL_EXE%" -h127.0.0.1 -uroot -p%DB_PASSWORD% --default-character-set=utf8mb4 -e "DROP DATABASE IF EXISTS simple_shop_e2e; CREATE DATABASE simple_shop_e2e CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
if errorlevel 1 (
  echo [E2E] failed to recreate database.
  exit /b 1
)

echo [E2E] done. Start the backend afterwards so Flyway builds the schema.
