# Build stage
FROM gradle:8.5-jdk17 AS build
WORKDIR /app

# Copy gradle files
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle ./gradle

# Copy source code
COPY services/notes-scheduler ./services/notes-scheduler

# Build the application
RUN gradle :services:notes-scheduler:installDist --no-daemon

# Runtime stage
FROM amazoncorretto:17-alpine
WORKDIR /app

# Copy built application from build stage
COPY --from=build /app/services/notes-scheduler/build/install/notes-scheduler ./

# Environment variables with defaults
ENV MCP_SERVER_URL=http://host.docker.internal:8082
ENV CRON_EXPRESSION="*/2 * * * *"
ENV SCHEDULER_ENABLED=true

# Add host.docker.internal to /etc/hosts for Windows/Mac Docker Desktop
# This allows the container to access services on the host
RUN echo "host.docker.internal host-gateway" >> /etc/hosts || true

# Run the application
CMD ["./bin/notes-scheduler"]
