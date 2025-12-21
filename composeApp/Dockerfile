# Multi-stage build for Compose Multiplatform frontend
FROM gradle:8.5-jdk17 AS build
WORKDIR /app

# Copy gradle files first for better layer caching
COPY gradle gradle
COPY gradlew gradlew.bat gradle.properties settings.gradle.kts ./
COPY build.gradle.kts ./
COPY gradle/libs.versions.toml gradle/

# Copy all modules
COPY shared ./shared
COPY server/build.gradle.kts ./server/
COPY composeApp ./composeApp

# Build the WebAssembly distribution
RUN gradle :composeApp:wasmJsBrowserDistribution --no-daemon

# Runtime stage with nginx
FROM nginx:alpine
WORKDIR /app

# Copy built files to nginx html directory
COPY --from=build /app/composeApp/build/dist/wasmJs/productionExecutable/ /usr/share/nginx/html/

# Copy nginx configuration
COPY composeApp/nginx.conf /etc/nginx/conf.d/default.conf

# Expose HTTP port
EXPOSE 80

# Start nginx
CMD ["nginx", "-g", "daemon off;"]
