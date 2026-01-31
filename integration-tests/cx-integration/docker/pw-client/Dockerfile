# PeSIT Wizard Client Docker image for integration testing
FROM eclipse-temurin:21-jre

# Install required packages
RUN apt-get update && apt-get install -y \
    curl \
    netcat-openbsd \
    && rm -rf /var/lib/apt/lists/*

# Create app user and directories
RUN useradd -m -s /bin/bash appuser && \
    mkdir -p /app /data/send /data/received /app/db && \
    chown -R appuser:appuser /app /data

WORKDIR /app

# Copy the application JAR
COPY pesitwizard-client-1.0.0-SNAPSHOT-exec.jar /app/pesitwizard-client.jar

# Copy configuration
COPY application-integration-test.yml /app/application-integration-test.yml

USER appuser

# Expose REST API port
EXPOSE 9081

# Health check
HEALTHCHECK --interval=10s --timeout=5s --retries=10 --start-period=60s \
    CMD curl -f http://localhost:9081/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/pesitwizard-client.jar", "--spring.profiles.active=integration-test,nosecurity", "--spring.config.additional-location=file:/app/"]
