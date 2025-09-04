@echo off
echo Generating Settings.java from defaultProperties.json using SettingsGenerator.java...
call mvn compile -q
if %errorlevel% neq 0 (
    echo Compilation failed!
    pause
    exit /b 1
)

@REM echo Generating Settings.java from defaultProperties.json...
@REM java -cp "target/classes;target/dependency/*" com.grumbo.SettingsGenerator src/main/resources/settings/defaultProperties.json src/main/java/com/grumbo/Settings.java
@REM if %errorlevel% neq 0 (
@REM     echo Generation failed!
@REM     pause
@REM     exit /b 1
@REM )

echo Done!
pause 