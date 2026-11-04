@echo off
setlocal
cd /d "%~dp0"
set "MOMENT_GRADLE_VERSION=8.11.1"
set "MOMENT_GRADLE_BIN=%CD%\.gradle-dist\gradle-%MOMENT_GRADLE_VERSION%\bin\gradle.bat"
if exist "%MOMENT_GRADLE_BIN%" goto run
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\bootstrap-gradle.ps1"
if errorlevel 1 exit /b 1
:run
call "%MOMENT_GRADLE_BIN%" %*
exit /b %errorlevel%
