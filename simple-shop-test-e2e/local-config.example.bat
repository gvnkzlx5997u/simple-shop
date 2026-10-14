@echo off
rem ============================================================
rem  TEMPLATE - local credentials for the E2E test environment
rem
rem  HOW TO USE
rem    1. copy this file to   local-config.bat   (same folder)
rem    2. fill in the two real values below
rem    3. local-config.bat is listed in .gitignore, so the real
rem       passwords never enter the repository (DEC-DB-12 rule 3)
rem
rem  NOTE: keep this file ASCII-only (cmd reads .bat as GBK).
rem ============================================================

rem  MySQL root password used by run-backend.bat and 2-run tests
set "E2E_DB_PASSWORD=CHANGE_ME"

rem  Seed seller account password (wiki: seed data)
set "E2E_SELLER_PASSWORD=CHANGE_ME"
