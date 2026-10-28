@echo off
chcp 65001 >nul
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0stop-all.ps1" %*
exit /b %ERRORLEVEL%
