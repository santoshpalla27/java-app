#!/bin/bash

# Debug script to check connectivity endpoint

echo "======================================"
echo "Connectivity Endpoint Debug"
echo "======================================"
echo ""

echo "1. Testing endpoint response:"
echo "----------------------------"
response=$(curl -s -w "\nHTTP_CODE:%{http_code}" http://localhost:8080/internal/connectivity)
echo "$response"
echo ""

echo "2. Checking backend logs for controller registration:"
echo "----------------------------------------------------"
docker logs sys-backend 2>&1 | grep -i "ConnectivityController\|connectivity.*controller" | head -20
echo ""

echo "3. Checking if endpoint is accessible:"
echo "--------------------------------------"
http_code=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/internal/connectivity)
echo "HTTP Status Code: $http_code"

if [ "$http_code" == "200" ]; then
    echo "✓ Endpoint is accessible"
elif [ "$http_code" == "404" ]; then
    echo "✗ Endpoint not found (404) - Controller not registered"
elif [ "$http_code" == "403" ]; then
    echo "✗ Forbidden (403) - Security blocking access"
else
    echo "✗ Unexpected status code: $http_code"
fi
echo ""

echo "4. Checking actuator health (for comparison):"
echo "--------------------------------------------"
curl -s http://localhost:8080/actuator/health | jq '.' 2>/dev/null || echo "Failed to get actuator health"
echo ""

echo "======================================"
echo "Diagnosis:"
echo "======================================"
if [ "$http_code" == "404" ]; then
    echo "The ConnectivityController is NOT being registered by Spring."
    echo "This means @ComponentScan is not picking up the com.platform package."
    echo ""
    echo "Solution: Make sure you have pulled the latest code with:"
    echo "  git pull"
    echo "  docker compose down"
    echo "  docker compose up --build -d"
elif [ "$http_code" == "403" ]; then
    echo "The endpoint exists but is blocked by security."
    echo "Make sure SecurityConfig allows /internal/** endpoints."
else
    echo "Check the response above for details."
fi
