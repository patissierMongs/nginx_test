#!/bin/bash

# Status Check Script
# Nginx Test Environment

echo "=========================================="
echo "Nginx Test Environment Status"
echo "=========================================="
echo ""

# Docker containers status
echo "Docker Containers:"
echo "------------------"
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" | grep -E "nginx|was-|redis-|kafka-|prometheus|grafana|jaeger|loki"

echo ""
echo "Service Health Checks:"
echo "----------------------"

check_health() {
    local name=$1
    local url=$2
    local timeout=5

    response=$(curl -s -o /dev/null -w "%{http_code}" --max-time $timeout "$url" 2>/dev/null)

    if [ "$response" = "200" ]; then
        echo -e "  \033[32m[OK]\033[0m $name"
    elif [ "$response" = "000" ]; then
        echo -e "  \033[31m[DOWN]\033[0m $name (connection failed)"
    else
        echo -e "  \033[33m[WARN]\033[0m $name (HTTP $response)"
    fi
}

# WAS Services
check_health "Nginx LB" "http://localhost/health"
check_health "WAS 1 (Tomcat)" "http://localhost:8081/health"
check_health "WAS 2 (Tomcat)" "http://localhost:8082/health"
check_health "WAS 3 (Spring Boot)" "http://localhost:8083/actuator/health"
check_health "WAS 4 (Spring Boot)" "http://localhost:8084/actuator/health"
check_health "WAS 5 (Node.js)" "http://localhost:3001/health"
check_health "WAS 6 (Node.js)" "http://localhost:3002/health"
check_health "WAS 7 (Flask)" "http://localhost:5001/health"
check_health "WAS 8 (Go)" "http://localhost:8001/health"

echo ""
echo "Infrastructure:"
check_health "Prometheus" "http://localhost:9090/-/healthy"
check_health "Grafana" "http://localhost:3000/api/health"
check_health "Jaeger" "http://localhost:16686"
check_health "Loki" "http://localhost:3100/ready"
check_health "Kafka UI" "http://localhost:8090"

echo ""
echo "Resource Usage:"
echo "---------------"
docker stats --no-stream --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}" | grep -E "nginx|was-|redis-|kafka-|prometheus|grafana|jaeger" | head -20

echo ""
