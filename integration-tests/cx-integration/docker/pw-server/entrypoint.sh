#!/bin/bash
# PeSIT Wizard Server container entrypoint

set -e

echo "Starting PeSIT Wizard Server..."

# Start the server with integration test configuration
exec java \
    -Dspring.profiles.active=integration-test \
    -Dpesit.api-key.admin-key=${PESIT_API_KEY:-integration-test-key} \
    -jar /app/pesitwizard-server.jar
