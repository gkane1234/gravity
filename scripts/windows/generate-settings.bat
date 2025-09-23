@echo off
call mvn compile -q
if %errorlevel% neq 0 (
    echo Compilation failed!
    pause
    exit /b 1
)