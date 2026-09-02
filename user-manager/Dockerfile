# syntax=docker/dockerfile:1

# --- Build stage ---
ARG JDK_IMAGE=eclipse-temurin:25-jdk
ARG JRE_IMAGE=eclipse-temurin:25-jre
FROM ${JDK_IMAGE} AS build
WORKDIR /workspace

# Bootstrap the shared Gradle distribution before module-specific build files.
# The wrapper and wrapper JAR are identical across services, so Docker can reuse
# this layer rather than downloading Gradle once per service image.
COPY gradlew ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --version --no-daemon

# Dependency layer: module-specific dependencies can be cached independently.
COPY settings.gradle.kts build.gradle.kts ./
RUN ./gradlew dependencies --no-daemon || true

# Build the application
COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

# --- Runtime stage ---
FROM ${JRE_IMAGE} AS runtime
WORKDIR /app

# Run as a non-root user
RUN groupadd --system spring && useradd --system --gid spring spring
USER spring:spring

COPY --from=build /workspace/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
