#!/bin/bash

# Stop All Services Script
# Nginx Test Environment

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

echo "=========================================="
echo "Stopping Nginx Test Environment"
echo "=========================================="

# Stop WAS services
echo "[1/3] Stopping WAS services..."
cd "$PROJECT_DIR"
docker-compose down --remove-orphans

# Stop observability stack
echo "[2/3] Stopping observability stack..."
cd "$PROJECT_DIR/infrastructure/observability"
docker-compose down --remove-orphans

# Stop infrastructure
echo "[3/3] Stopping infrastructure services..."
cd "$PROJECT_DIR/infrastructure/kafka"
docker-compose down --remove-orphans

cd "$PROJECT_DIR/infrastructure/redis"
docker-compose down --remove-orphans

echo ""
echo "All services stopped."
echo ""
echo "To remove all data volumes, run:"
echo "  docker volume prune"
echo ""
