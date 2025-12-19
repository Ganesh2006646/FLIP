@echo off
:: --- CONFIGURATION ---
:: Ensure this path matches your actual JDK installation
set "JAVA_HOME=C:\Program Files\Java\jdk-23"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo ==========================================
echo       FLIP WARS - SEMESTER PROJECT
echo ==========================================

:: 1. Check if source exists
if not exist src\main\java\com\flipwars\Main.java (
    echo ERROR: src/main/java/com/flipwars/Main.java not found!
    echo Please make sure the source code is in the correct folder structure.
    pause
    exit /b
)

:: 2. Create a 'bin' folder to keep things clean (Optional but good practice)
if not exist bin mkdir bin

echo Compiling...
:: -d bin: Puts the class files into the 'bin' folder
:: -sourcepath src/main/java: Tells compiler where to look for other classes
javac -d bin -sourcepath src/main/java src/main/java/com/flipwars/*.java

if %errorlevel% neq 0 (
    echo.
    echo [!] COMPILATION FAILED.
    echo Please check your Java code for errors.
    pause
    exit /b
)

echo Running Game...
echo.
:: Run the class. Note: "com.flipwars.Main" matches the 'package' line in your code.
:: -cp bin: Tells Java to look for the class inside the 'bin' folder.
java -cp bin com.flipwars.Main

pause