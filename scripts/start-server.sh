#!/bin/bash
# Start PeSIT Wizard Server (standalone mode)

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

echo "=== Starting PeSIT Wizard Server ==="

cd "$PROJECT_DIR/pesitwizard-server"

# Check if JAR exists
JAR_FILE=$(ls target/pesitwizard-server-*.jar 2>/dev/null | head -1)
if [ -z "$JAR_FILE" ]; then
  echo "Building pesitwizard-server..."
  cd "$PROJECT_DIR/pesitwizard-pesit" && mvn install -DskipTests -q
  cd "$PROJECT_DIR/pesitwizard-server" && mvn package -DskipTests -q
  JAR_FILE=$(ls target/pesitwizard-server-*.jar 2>/dev/null | head -1)
fi

if [ -z "$JAR_FILE" ]; then
  echo "ERROR: Could not find or build pesitwizard-server JAR"
  exit 1
fi

echo "Starting $JAR_FILE..."
java -jar "$JAR_FILE"
