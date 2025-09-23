#Used to start the application on iOS
cd "$(dirname "$0")"
mvn -q -DskipTests compile
java -XstartOnFirstThread -cp "target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)" com.grumbo.Main