@echo off
:: =========================================================
:: FLIP WARS — Team-13  (JavaFX 21 Edition)
:: Portable: uses relative paths — works on any drive/lab PC
:: =========================================================

:: ── Java home (adjust if JDK is not in Program Files) ────
set "JAVA_HOME=C:\Program Files\Java\jdk-23"
set "PATH=%JAVA_HOME%\bin;%PATH%"

:: ── JavaFX SDK — RELATIVE path (portable: runs on any drive)
set "JAVAFX_HOME=.\lib\javafx"
set "JAVAFX_MODS=javafx.controls,javafx.graphics,javafx.base"

echo =========================================================
echo          FLIP WARS — Team-13
echo     Design and Analysis of Algorithms Project
echo =========================================================
echo.

:: ── Sanity checks ─────────────────────────────────────────
if not exist "src\main\java\com\flipwars\FlipWarsApp.java" (
    echo [ERROR] Source not found. Run from the project root folder.
    pause & exit /b 1
)
if not exist "%JAVAFX_HOME%\lib\javafx.controls.jar" (
    echo [ERROR] JavaFX SDK not found at %JAVAFX_HOME%
    echo         Expected: .\lib\javafx\lib\javafx.controls.jar
    pause & exit /b 1
)

:: ── Compile ───────────────────────────────────────────────
if not exist bin mkdir bin
echo [1/2] Compiling...

:: Build the source file list (CMD does NOT expand *.java globs in javac)
dir /s /b "src\main\java\com\flipwars\*.java" > sources.txt

javac ^
    --module-path "%JAVAFX_HOME%\lib" ^
    --add-modules %JAVAFX_MODS% ^
    -d bin ^
    @sources.txt

if %errorlevel% neq 0 (
    echo.
    echo [!] COMPILATION FAILED. Check errors above.
    del sources.txt 2>nul
    pause & exit /b 1
)
del sources.txt 2>nul

:: ── Run ───────────────────────────────────────────────────
echo [2/2] Launching game...
echo.
java ^
    --module-path "%JAVAFX_HOME%\lib" ^
    --add-modules %JAVAFX_MODS% ^
    -cp bin ^
    com.flipwars.FlipWarsApp

pause