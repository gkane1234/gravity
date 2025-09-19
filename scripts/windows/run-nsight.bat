@echo off
setlocal
cd /d %~dp0

rem Build shaded jar and copy runtime deps
mvn -q -DskipTests=true package
if errorlevel 1 exit /b %errorlevel%

set CP=target\gravitychunk-1.0-SNAPSHOT.jar;target\lib\*
"%JAVA_HOME%\bin\java.exe" -Xmx24g -Xms1g -cp "%CP%" com.grumbo.Main


