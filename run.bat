@echo off
set "JAVA_HOME=C:\Program Files\Java\jdk-23"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo Starting Flip Wars with Maven...
call mvn clean javafx:run
if %errorlevel% neq 0 (
    echo.
    echo Compilation Failed!
    pause
    exit /b
)
pause
