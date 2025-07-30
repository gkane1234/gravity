@echo off
echo Compiling project first...
call mvn compile -q
if %errorlevel% neq 0 (
    echo Compilation failed!
    pause
    exit /b 1
)

echo Generating Settings.java from defaultProperties.json...
java -cp "target/classes;target/dependency/*" com.grumbo.SettingsGenerator src/main/resources/defaultProperties.json src/main/java/com/grumbo/Settings.java
if %errorlevel% neq 0 (
    echo Generation failed!
    pause
    exit /b 1
)

echo Done!
pause 