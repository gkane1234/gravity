@echo off
echo Generating Settings.java from defaultProperties.json...
mvn exec:java -Dexec.mainClass="com.grumbo.SettingsGenerator" -Dexec.args="src/main/resources/defaultProperties.json src/main/java/com/grumbo/Settings.java"
echo Done!
pause 