# PeSIT Wizard Server Docker image for integration testing
FROM eclipse-temurin:21-jre

WORKDIR /app

# Copy the server JAR
COPY pesitwizard-server.jar /app/pesitwizard-server.jar

# Copy the entrypoint script
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

# Create data directories
RUN mkdir -p /data/received /data/send

# Expose ports
# 5001 - PeSIT protocol
# 8080 - REST API
EXPOSE 5001 8080

# Health check
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["/entrypoint.sh"]
