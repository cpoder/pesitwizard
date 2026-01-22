# Runtime stage - uses pre-built JAR
FROM eclipse-temurin:21-jre-alpine

# Add non-root user
RUN addgroup -g 1000 pesitwizard && \
    adduser -u 1000 -G pesitwizard -s /bin/sh -D pesitwizard

WORKDIR /app

# Copy the pre-built executable JAR (Spring Boot fat JAR)
COPY target/pesitwizard-client-*-exec.jar app.jar

# Copy connector JARs (sftp, s3) - use wildcard to handle empty dir
COPY connector[s]/ connectors/

# Create directories for data
RUN mkdir -p /data /app/data /app/connectors && \
    chown -R pesitwizard:pesitwizard /data /app

# Switch to non-root user
USER pesitwizard

# Expose ports
# 8080 - REST API (configurable via SERVER_PORT)
EXPOSE 8080

# Default server port (can be overridden)
ENV SERVER_PORT=8080

# Health check (uses SERVER_PORT)
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -q --spider http://localhost:${SERVER_PORT}/actuator/health || exit 1

# JVM options for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dserver.port=${SERVER_PORT} -jar app.jar serve"]
