#!/bin/bash
cd "$(dirname "$0")"
# Regenerate Settings.java is now wired in pom.xml (generate-sources phase)
mvn -q -DskipTests compile
java -XstartOnFirstThread -cp "target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)" com.grumbo.Main