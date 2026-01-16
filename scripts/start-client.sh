#!/bin/bash
# Start pesitwizard-client backend and UI

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

# Vault configuration (dev mode)
# Set PESITWIZARD_SECURITY_VAULT_TOKEN in your environment or .env file
export PESITWIZARD_SECURITY_VAULT_ADDRESS=${PESITWIZARD_SECURITY_VAULT_ADDRESS:-http://localhost:30200}
export PESITWIZARD_SECURITY_VAULT_AUTH_METHOD=${PESITWIZARD_SECURITY_VAULT_AUTH_METHOD:-token}
# export PESITWIZARD_SECURITY_VAULT_TOKEN=your-token-here

echo "Starting PeSIT Wizard Client..."

# Kill existing processes
pkill -f "spring-boot:run.*pesitwizard-client" 2>/dev/null
pkill -f "PesitClientApplication" 2>/dev/null
# Kill vite processes in pesitwizard-client-ui directory
for pid in $(pgrep -f "node.*vite"); do
  if lsof -p $pid 2>/dev/null | grep -q pesitwizard-client-ui; then
    kill $pid 2>/dev/null
  fi
done
sleep 1

# Use port 9081 to avoid conflicts
export SERVER_PORT=9081

# Ensure data directory exists
mkdir -p "$PROJECT_DIR/pesitwizard-client/data"

# Start backend
cd "$PROJECT_DIR/pesitwizard-client"
nohup env SERVER_PORT="$SERVER_PORT" mvn spring-boot:run -Dspring-boot.run.arguments=serve -DskipTests > /tmp/pesitwizard-client.log 2>&1 &
echo "Client backend starting (log: /tmp/pesitwizard-client.log)"

# Wait for backend
echo -n "Waiting for backend..."
for i in {1..30}; do
  if curl -s -o /dev/null -w "%{http_code}" http://localhost:9081/actuator/health 2>/dev/null | grep -q "200\|401"; then
    echo " ready!"
    break
  fi
  echo -n "."
  sleep 1
done

# Start UI
cd "$PROJECT_DIR/pesitwizard-client-ui"
nohup npm run dev -- --port 3001 --strictPort > /tmp/pesitwizard-client-ui.log 2>&1 &
sleep 2
echo "Client UI started at http://localhost:3001"
