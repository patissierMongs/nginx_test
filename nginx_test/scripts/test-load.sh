#!/bin/bash

# Load Test Script
# Simple load testing for Nginx Test Environment

set -e

BASE_URL="${1:-http://localhost}"
DURATION="${2:-30}"
CONCURRENCY="${3:-10}"

echo "=========================================="
echo "Load Test - Nginx Test Environment"
echo "=========================================="
echo "Base URL: $BASE_URL"
echo "Duration: ${DURATION}s"
echo "Concurrency: $CONCURRENCY"
echo ""

# Check if hey is installed
if ! command -v hey &> /dev/null; then
    echo "Installing 'hey' load testing tool..."
    go install github.com/rakyll/hey@latest || {
        echo "Please install 'hey': go install github.com/rakyll/hey@latest"
        echo "Or use: brew install hey (macOS)"
        exit 1
    }
fi

echo "Testing endpoints..."
echo ""

# Test each WAS through different paths
endpoints=(
    "/api/springboot/info"
    "/api/nodejs/info"
    "/api/flask/info"
    "/api/go/info"
    "/api/info"
)

for endpoint in "${endpoints[@]}"; do
    echo "Testing: $endpoint"
    echo "---"
    hey -z "${DURATION}s" -c "$CONCURRENCY" -m GET "${BASE_URL}${endpoint}" 2>/dev/null | grep -E "Total:|Requests/sec:|Average:|Fastest:|Slowest:|Status code"
    echo ""
done

echo "=========================================="
echo "Load Test Complete"
echo "=========================================="
echo ""
echo "View detailed traces at: http://localhost:16686"
echo "View metrics at: http://localhost:3000"
