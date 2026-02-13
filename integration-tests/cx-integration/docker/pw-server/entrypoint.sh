#!/bin/bash
# PeSIT Wizard Server container entrypoint

set -e

echo "Starting PeSIT Wizard Server..."

# If certificate files are mounted, convert them to env vars the server expects
# The server uses PESIT_SSL_KEYSTORE_DATA (Base64) and PESIT_SSL_CA_CERT_PEM (PEM text)
if [ -n "$PESIT_SSL_KEYSTORE_PATH" ] && [ -f "$PESIT_SSL_KEYSTORE_PATH" ]; then
    echo "Loading keystore from file: $PESIT_SSL_KEYSTORE_PATH"
    export PESIT_SSL_KEYSTORE_DATA=$(base64 -w0 "$PESIT_SSL_KEYSTORE_PATH")
fi

if [ -n "$PESIT_SSL_CA_CERT_PATH" ] && [ -f "$PESIT_SSL_CA_CERT_PATH" ]; then
    echo "Loading CA certificate from file: $PESIT_SSL_CA_CERT_PATH"
    export PESIT_SSL_CA_CERT_PEM=$(cat "$PESIT_SSL_CA_CERT_PATH")
fi

# Start the server with integration test configuration
exec java \
    -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:-integration-test} \
    -Dpesit.api-key.admin-key=${PESIT_API_KEY:-integration-test-key} \
    -jar /app/pesitwizard-server.jar
