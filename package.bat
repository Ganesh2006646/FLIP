@echo off
:: =============================================================
:: FLIP WARS — Team-13 — Build + Package Script
:: Produces: FlipWars.jar (app-only) + FlipWars-installer.exe
:: =============================================================
setlocal

set "JAVA_HOME=C:\Program Files\Java\jdk-23"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "JAVAFX_HOME=.\lib\javafx"
set "JAVAFX_MODS=javafx.controls,javafx.graphics,javafx.base"
set "APP_VERSION=3.0"

echo ==========================================================
echo  FLIP WARS — Build ^& Package
echo ==========================================================
echo.

:: ── Step 1: Clean ─────────────────────────────────────────
echo [1/4] Cleaning...
if exist bin     rmdir /s /q bin
if exist staging rmdir /s /q staging
if exist dist    rmdir /s /q dist
mkdir bin
mkdir staging

:: ── Step 2: Compile ───────────────────────────────────────
echo [2/4] Compiling...
dir /s /b "src\main\java\com\flipwars\*.java" > sources.txt

javac ^
    --module-path "%JAVAFX_HOME%\lib" ^
    --add-modules %JAVAFX_MODS% ^
    -d bin ^
    @sources.txt

if %errorlevel% neq 0 (
    echo [!] COMPILATION FAILED.
    del sources.txt 2>nul
    pause & exit /b 1
)
del sources.txt 2>nul

:: ── Step 3: Build app JAR ─────────────────────────────────
echo [3/4] Building FlipWars.jar...
jar cfe FlipWars.jar com.flipwars.FlipWarsApp -C bin .

:: ── Step 4: jpackage → .exe installer ─────────────────────
echo [4/4] Packaging with jpackage (standalone .exe installer)...
echo         This bundles a private JRE + JavaFX — users need NOTHING installed.
echo.

:: Copy app jar + javafx jars into a flat staging dir for jpackage
copy FlipWars.jar staging\ >nul
copy "%JAVAFX_HOME%\lib\javafx.base.jar"     staging\ >nul
copy "%JAVAFX_HOME%\lib\javafx.controls.jar"  staging\ >nul
copy "%JAVAFX_HOME%\lib\javafx.graphics.jar"  staging\ >nul

:: Copy JavaFX native DLLs into staging (required at runtime)
copy "%JAVAFX_HOME%\bin\*.dll" staging\ >nul 2>nul
copy "%JAVAFX_HOME%\lib\*.dll" staging\ >nul 2>nul

:: WiX Toolset check — jpackage needs it for .exe/.msi on Windows
:: If WiX is not installed, we fall back to --type app-image (portable folder)
where wix >nul 2>nul
if %errorlevel% neq 0 (
    echo [INFO] WiX Toolset not found — building portable app-image instead of .exe installer.
    echo        To get a proper .exe installer, install WiX: https://wixtoolset.org/
    echo.

    jpackage ^
        --type app-image ^
        --name FlipWars ^
        --app-version %APP_VERSION% ^
        --vendor "Team-13 DAA" ^
        --input staging ^
        --main-jar FlipWars.jar ^
        --main-class com.flipwars.FlipWarsApp ^
        --module-path "%JAVAFX_HOME%\lib" ^
        --add-modules %JAVAFX_MODS% ^
        --dest dist ^
        --java-options "--add-opens javafx.graphics/com.sun.javafx.application=ALL-UNNAMED"

    if %errorlevel% neq 0 (
        echo [!] jpackage FAILED. See errors above.
        pause & exit /b 1
    )

    echo.
    echo =========================================================
    echo  SUCCESS — Portable app created at: dist\FlipWars\
    echo  Run it with: dist\FlipWars\FlipWars.exe
    echo  Zip the dist\FlipWars\ folder to distribute.
    echo =========================================================
) else (
    jpackage ^
        --type exe ^
        --name FlipWars ^
        --app-version %APP_VERSION% ^
        --vendor "Team-13 DAA" ^
        --input staging ^
        --main-jar FlipWars.jar ^
        --main-class com.flipwars.FlipWarsApp ^
        --module-path "%JAVAFX_HOME%\lib" ^
        --add-modules %JAVAFX_MODS% ^
        --dest dist ^
        --win-shortcut ^
        --win-menu ^
        --java-options "--add-opens javafx.graphics/com.sun.javafx.application=ALL-UNNAMED"

    if %errorlevel% neq 0 (
        echo [!] jpackage FAILED. See errors above.
        pause & exit /b 1
    )

    echo.
    echo =========================================================
    echo  SUCCESS — Installer created at: dist\FlipWars-%APP_VERSION%.exe
    echo  Users double-click it to install. No Java needed.
    echo =========================================================
)

:: Cleanup staging
rmdir /s /q staging

pause
