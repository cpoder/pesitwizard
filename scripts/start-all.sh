#!/bin/bash
# Start all Vectis services

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

"$SCRIPT_DIR/start-admin.sh"
"$SCRIPT_DIR/start-client.sh"

echo ""
echo "=== All services started ==="
echo "Admin UI:  http://localhost:3000"
echo "Client UI: http://localhost:3001"
