@echo off
rem Windows wrapper for the cross-platform reset script (node + mysql2).
rem 配置（主机/账号/口令/库名）来自项目根的 .env，模板 .env.example；本脚本不再读 local-config.bat。
rem Usage: scripts\reset-e2e-db.bat [--db=库名] [--dry-run]
setlocal
cd /d "%~dp0.."

node "scripts\reset-e2e-db.js" %*
exit /b %ERRORLEVEL%
