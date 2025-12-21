# Runtime stage - uses pre-built application
# To build: First run `gradlew :services:notes-scheduler:installDist` locally
FROM amazoncorretto:21-alpine-jdk
WORKDIR /app

# Copy pre-built application from host
COPY services/notes-scheduler/build/install/notes-scheduler ./

# Environment variables with defaults
ENV SERVER_URL=http://host.docker.internal:8080
ENV CRON_EXPRESSION="*/2 * * * *"
ENV SCHEDULER_ENABLED=true

# Add host.docker.internal to /etc/hosts for Windows/Mac Docker Desktop
# This allows the container to access services on the host
RUN echo "host.docker.internal host-gateway" >> /etc/hosts || true

# Run the application
CMD ["./bin/notes-scheduler"]
