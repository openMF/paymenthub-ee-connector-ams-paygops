FROM eclipse-temurin:21-jre
# 5000 = Camel REST routes, 8080 = Spring Boot / actuator
EXPOSE 5000 8080

# Copy the boot jar by name, not `*.jar`: `gradlew build` also produces a
# -plain.jar, which has no main class and sorts first in the glob.
COPY build/libs/app.jar app.jar
CMD ["java", "-jar", "app.jar"]
