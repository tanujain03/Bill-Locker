# Builds and runs the Spring Boot backend in one container (used by Render).
# Two stages: a big image with Maven builds the jar, a small Java-only image runs it.

# ---- 1. Build ---------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# pom.xml first: the dependency download is cached until pom.xml changes.
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
# Tests need Docker (Testcontainers), which isn't available here; they run on your PC.
RUN mvn -q -B package -DskipTests

# ---- 2. Run -----------------------------------------------------------------------
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/bill-locker-0.0.1-SNAPSHOT.jar app.jar
# Use most of the container's memory for Java (free hosts have little).
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
# All settings (DATABASE_URL, JWT_SECRET, ...) come from the host's environment variables.
ENTRYPOINT ["java", "-jar", "app.jar"]
