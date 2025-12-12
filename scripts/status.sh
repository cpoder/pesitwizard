#!/bin/bash
# Check status of all Vectis services

echo "=== Vectis Services Status ==="
echo ""

# PostgreSQL (on port 5435)
if podman ps 2>/dev/null | grep -q vectis-postgres; then
  echo "PostgreSQL:     RUNNING at localhost:5435"
else
  echo "PostgreSQL:     STOPPED"
fi

# Admin backend - check by port (9080 to avoid conflicts)
if curl -s -o /dev/null -w "%{http_code}" http://localhost:9080/actuator/health 2>/dev/null | grep -qE "200|401"; then
  echo "Admin Backend:  RUNNING at http://localhost:9080"
else
  echo "Admin Backend:  STOPPED"
fi

# Admin UI - check by port
if curl -s -o /dev/null http://localhost:3000 2>/dev/null; then
  echo "Admin UI:       RUNNING at http://localhost:3000"
else
  echo "Admin UI:       STOPPED"
fi

# Client backend - check by port (9081 to avoid conflicts)
if curl -s -o /dev/null -w "%{http_code}" http://localhost:9081/actuator/health 2>/dev/null | grep -qE "200|401"; then
  echo "Client Backend: RUNNING at http://localhost:9081"
else
  echo "Client Backend: STOPPED"
fi

# Client UI - check by port
if curl -s -o /dev/null http://localhost:3001 2>/dev/null; then
  echo "Client UI:      RUNNING at http://localhost:3001"
else
  echo "Client UI:      STOPPED"
fi

echo ""
echo "=== Port Forwarding ==="
if pgrep -f "port-forward.*vectis-server" >/dev/null 2>&1; then
  echo "Vectis Server:   ACTIVE (localhost:8080 -> vectis-server-api:8080)"
else
  echo "Vectis Server:   NOT ACTIVE"
  echo "                Run: kubectl port-forward svc/vectis-server-api 8080:8080 -n default &"
fi

echo ""
echo "=== Kubernetes Clusters ==="
kubectl get pods -l app.kubernetes.io/name=vectis-server 2>/dev/null || echo "No K8s clusters found"
