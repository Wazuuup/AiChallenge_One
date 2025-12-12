# Multi-stage build for Kotlin server
FROM gradle:8.5-jdk17 AS build
WORKDIR /app

# Copy gradle files first for better layer caching
COPY gradle gradle
COPY gradlew gradlew.bat gradle.properties settings.gradle.kts ./
COPY build.gradle.kts ./
COPY gradle/libs.versions.toml gradle/

# Copy all modules
COPY shared ./shared
COPY server ./server
COPY composeApp/build.gradle.kts ./composeApp/

# Build the server distribution
RUN gradle :server:installDist --no-daemon

# Runtime stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy the built application
COPY --from=build /app/server/build/install/server ./

# Expose the server port
EXPOSE 8080

# Run the server
CMD ["./bin/server"]
