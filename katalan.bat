@echo off
REM katalan Runner - Windows Batch Script
REM Usage: katalan.bat run -tc test.groovy

setlocal

REM Get the directory of this script
set "SCRIPT_DIR=%~dp0"

REM Find the JAR file
set "JAR_FILE=%SCRIPT_DIR%target\katalan-runner-1.1.2.jar"

if not exist "%JAR_FILE%" (
    echo Error: katalan-runner-1.1.2.jar not found!
    echo Please build the project first: mvn clean package
    exit /b 1
)

REM Run katalan
java -jar "%JAR_FILE%" %*
