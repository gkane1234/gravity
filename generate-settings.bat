@echo off
echo Generating Settings.java from defaultProperties.json using SettingsGenerator.java...
call mvn compile -q
if %errorlevel% neq 0 (
    echo Compilation failed!
    pause
    exit /b 1
)

echo Done!