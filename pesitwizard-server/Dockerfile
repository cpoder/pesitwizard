# Runtime stage - uses pre-built JAR
FROM eclipse-temurin:21-jre-alpine

# Security: upgrade all OS packages to patch known CVEs (zlib, busybox, etc.)
RUN apk update && apk upgrade --no-cache && rm -rf /var/cache/apk/*

# Add non-root user
RUN addgroup -g 1000 pesit && \
    adduser -u 1000 -G pesit -s /bin/sh -D pesit

WORKDIR /app

# Copy the pre-built JAR (exec classifier for Spring Boot fat JAR)
COPY target/pesitwizard-server-*-exec.jar app.jar

# Create directories for data (both /data and /app paths for flexibility)
RUN mkdir -p /data/db /data/received /data/send \
             /app/received /app/send /app/config/jgroups && \
    chown -R pesit:pesit /data /app

# Switch to non-root user
USER pesit

# Expose ports
# 8080 - REST API / Management
# 6502 - PeSIT protocol (plaintext)
# 5001 - PeSIT protocol (TLS)
# 7800 - JGroups cluster communication
EXPOSE 8080 6502 5001 7800

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -q --spider http://localhost:8080/actuator/health || exit 1

# JVM options for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0"

# Default JGroups config location
ENV PESIT_CLUSTER_CONFIG="/app/config/jgroups/kube.xml"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
