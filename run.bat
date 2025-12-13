@echo off
echo Compiling Flip Wars...
javac Main.java
if %errorlevel% neq 0 (
    echo Compilation Failed!
    echo Ensure you have Java and JavaFX configured.
    pause
    exit /b
)

echo Starting Flip Wars...
java Main
pause
