#!/bin/bash
# Start vectis-admin backend and UI
# Also sets up port-forwarding to Kubernetes Vectis server if deployed

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

echo "=========================================="
echo "  Starting Vectis Admin Environment"
echo "=========================================="

# Ensure PostgreSQL is running on port 5435 (to avoid conflicts with system postgres)
echo ""
echo "[1/5] Checking PostgreSQL..."
if ! podman ps 2>/dev/null | grep -q vectis-postgres; then
  echo "  Starting PostgreSQL container..."
  podman start vectis-postgres 2>/dev/null || {
    podman rm -f vectis-postgres 2>/dev/null
    # Use a named volume for data persistence
    podman volume create vectis-pgdata 2>/dev/null
    podman run -d --name vectis-postgres \
      -e POSTGRES_USER=vectis \
      -e POSTGRES_PASSWORD=vectis \
      -e POSTGRES_DB=vectis \
      -v vectis-pgdata:/var/lib/postgresql/data \
      -p 5435:5432 \
      postgres:15-alpine
  }
  sleep 3
fi
echo "  PostgreSQL running on port 5435"

# Export database URL for the application
export DATABASE_URL="jdbc:postgresql://localhost:5435/vectis"
export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5435/vectis"
export SPRING_DATASOURCE_USERNAME="vectis"
export SPRING_DATASOURCE_PASSWORD="vectis"
export SERVER_PORT=9080

# Kill existing processes
echo ""
echo "[2/5] Cleaning up existing processes..."
pkill -f "spring-boot:run.*vectis-admin" 2>/dev/null
pkill -f "PesitAdminApplication" 2>/dev/null
pkill -f "port-forward.*vectis-server" 2>/dev/null
# Kill vite processes in vectis-admin-ui directory
for pid in $(pgrep -f "node.*vite"); do
  if lsof -p $pid 2>/dev/null | grep -q vectis-admin-ui; then
    kill $pid 2>/dev/null
  fi
done
sleep 1

# Start backend
echo ""
echo "[3/5] Starting Admin Backend..."
cd "$PROJECT_DIR/vectis-admin"
nohup env DATABASE_URL="$DATABASE_URL" SPRING_DATASOURCE_URL="$SPRING_DATASOURCE_URL" SPRING_DATASOURCE_USERNAME="$SPRING_DATASOURCE_USERNAME" SPRING_DATASOURCE_PASSWORD="$SPRING_DATASOURCE_PASSWORD" SERVER_PORT="$SERVER_PORT" mvn spring-boot:run -DskipTests > /tmp/vectis-admin.log 2>&1 &
echo "  Backend starting (log: /tmp/vectis-admin.log)"

# Wait for backend
echo -n "  Waiting for backend..."
for i in {1..30}; do
  if curl -s -o /dev/null -w "%{http_code}" http://localhost:9080/actuator/health 2>/dev/null | grep -q "200\|401"; then
    echo " ready!"
    break
  fi
  echo -n "."
  sleep 1
done

# Start UI
echo ""
echo "[4/5] Starting Admin UI..."
cd "$PROJECT_DIR/vectis-admin-ui"
nohup npm run dev -- --strictPort > /tmp/vectis-admin-ui.log 2>&1 &
sleep 2
echo "  UI started at http://localhost:3000"

# Setup port-forwarding to Kubernetes Vectis server if deployed
echo ""
echo "[5/5] Setting up Kubernetes port-forwarding..."
if kubectl get svc vectis-server-api -n default >/dev/null 2>&1; then
  # Check if port 8080 is already in use
  if ! lsof -i :8080 >/dev/null 2>&1; then
    nohup kubectl port-forward svc/vectis-server-api 8080:8080 -n default > /tmp/vectis-portforward.log 2>&1 &
    sleep 2
    if lsof -i :8080 >/dev/null 2>&1; then
      echo "  Port-forwarding active: localhost:8080 -> vectis-server-api:8080"
    else
      echo "  WARNING: Port-forwarding failed to start"
      echo "  Run manually: kubectl port-forward svc/vectis-server-api 8080:8080 -n default &"
    fi
  else
    echo "  Port 8080 already in use (port-forwarding may already be active)"
  fi
else
  echo "  No Vectis server deployed yet (vectis-server-api service not found)"
  echo "  Port-forwarding will be needed after deployment:"
  echo "    kubectl port-forward svc/vectis-server-api 8080:8080 -n default &"
fi

echo ""
echo "=========================================="
echo "  Vectis Admin Environment Ready"
echo "=========================================="
echo ""
echo "  Admin UI:      http://localhost:3000"
echo "  Admin API:     http://localhost:9080"
echo "  Credentials:   admin / admin"
echo ""
echo "  Logs:"
echo "    Backend:     tail -f /tmp/vectis-admin.log"
echo "    UI:          tail -f /tmp/vectis-admin-ui.log"
echo "    Port-fwd:    tail -f /tmp/vectis-portforward.log"
echo ""
echo "  Stop all:      ./scripts/stop-all.sh"
echo "=========================================="
