# Runtime stage - uses pre-built JAR
FROM eclipse-temurin:21-jre-alpine

# Add non-root user
RUN addgroup -g 1000 pesit && \
    adduser -u 1000 -G pesit -s /bin/sh -D pesit

WORKDIR /app

# Copy the pre-built JAR
COPY target/vectis-client-*.jar app.jar

# Create directories for data
RUN mkdir -p /data && \
    chown -R pesit:pesit /data /app

# Switch to non-root user
USER pesit

# Expose ports
# 8081 - REST API
EXPOSE 8081

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -q --spider http://localhost:8081/actuator/health || exit 1

# JVM options for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar serve"]
