#!/bin/bash

# Start All Services Script
# Nginx Test Environment

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

echo "=========================================="
echo "Starting Nginx Test Environment"
echo "=========================================="

# Create network if not exists
echo "[1/5] Creating Docker network..."
docker network create nginx-test-net --subnet=172.20.0.0/16 2>/dev/null || true

# Start infrastructure (Redis, Kafka)
echo "[2/5] Starting infrastructure services..."
cd "$PROJECT_DIR/infrastructure/redis"
docker-compose up -d

cd "$PROJECT_DIR/infrastructure/kafka"
docker-compose up -d

echo "Waiting for infrastructure to be ready..."
sleep 30

# Start observability stack
echo "[3/5] Starting observability stack..."
cd "$PROJECT_DIR/infrastructure/observability"
docker-compose up -d

echo "Waiting for observability to be ready..."
sleep 20

# Start WAS services
echo "[4/5] Starting WAS services..."
cd "$PROJECT_DIR"
docker-compose up -d --build

echo "Waiting for WAS services to be ready..."
sleep 30

# Health check
echo "[5/5] Running health checks..."
echo ""

check_service() {
    local name=$1
    local url=$2
    if curl -s -o /dev/null -w "%{http_code}" "$url" | grep -q "200"; then
        echo "  [OK] $name"
    else
        echo "  [FAIL] $name"
    fi
}

echo "Service Health Status:"
check_service "Nginx LB" "http://localhost/health"
check_service "WAS 1 (Tomcat)" "http://localhost:8081/health"
check_service "WAS 2 (Tomcat)" "http://localhost:8082/health"
check_service "WAS 3 (Spring Boot)" "http://localhost:8083/actuator/health"
check_service "WAS 4 (Spring Boot)" "http://localhost:8084/actuator/health"
check_service "WAS 5 (Node.js)" "http://localhost:3001/health"
check_service "WAS 6 (Node.js)" "http://localhost:3002/health"
check_service "WAS 7 (Flask)" "http://localhost:5001/health"
check_service "WAS 8 (Go)" "http://localhost:8001/health"
check_service "Prometheus" "http://localhost:9090/-/healthy"
check_service "Grafana" "http://localhost:3000/api/health"
check_service "Jaeger" "http://localhost:16686"

echo ""
echo "=========================================="
echo "Environment Ready!"
echo "=========================================="
echo ""
echo "Access Points:"
echo "  - Nginx LB:      http://localhost"
echo "  - Prometheus:    http://localhost:9090"
echo "  - Grafana:       http://localhost:3000 (admin/admin123)"
echo "  - Jaeger:        http://localhost:16686"
echo "  - Kafka UI:      http://localhost:8090"
echo ""
echo "API Endpoints:"
echo "  - /api/springboot/*"
echo "  - /api/nodejs/*"
echo "  - /api/flask/*"
echo "  - /api/go/*"
echo ""
