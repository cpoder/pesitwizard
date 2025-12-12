#!/bin/bash
# Stop all Vectis services

echo "Stopping all Vectis services..."

# Stop backend processes
pkill -f "spring-boot:run.*vectis-admin" 2>/dev/null
pkill -f "spring-boot:run.*vectis-client" 2>/dev/null
pkill -f "PesitAdminApplication" 2>/dev/null
pkill -f "PesitClientApplication" 2>/dev/null

# Stop UI processes
pkill -f "vite.*3000" 2>/dev/null
pkill -f "vite.*3001" 2>/dev/null

# Stop port-forwarding
pkill -f "port-forward.*vectis-server" 2>/dev/null

echo "All services stopped."
