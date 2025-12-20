#!/bin/bash

# Quick test for No Dependencies (Resilience) scenario only

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo "======================================"
echo "Testing: No Dependencies (Resilience)"
echo "======================================"
echo ""

# Cleanup first
echo -e "${YELLOW}Cleaning up any existing containers...${NC}"
docker compose down -v 2>/dev/null || true
docker compose -f docker-compose.mysql-ha.yml down -v 2>/dev/null || true
docker compose -f docker-compose.kafka-cluster.yml down -v 2>/dev/null || true
docker compose -f docker-compose.no-deps.yml down -v 2>/dev/null || true
sleep 5

echo -e "${BLUE}Starting backend with no dependencies...${NC}"
docker compose -f docker-compose.no-deps.yml up -d --build

# Wait for app
echo -e "${BLUE}Waiting for app to start...${NC}"
max_attempts=60
attempt=0
app_started=false

while [ $attempt -lt $max_attempts ]; do
    if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
        echo -e "${GREEN}App is up!${NC}"
        app_started=true
        break
    fi
    ((attempt++))
    echo -n "."
    sleep 2
done
echo ""

if [ "$app_started" = "true" ]; then
    echo "Waiting for connectivity checks to run..."
    sleep 15
    
    echo -e "${BLUE}Fetching connectivity status...${NC}"
    response=$(curl -s http://localhost:8080/internal/connectivity)
    
    echo "Full response:"
    echo $response | jq '.'
    
    mysql_state=$(echo $response | jq -r '.mysql.state')
    redis_state=$(echo $response | jq -r '.redis.state')
    kafka_state=$(echo $response | jq -r '.kafka.state')
    
    echo ""
    echo "Dependency States:"
    echo "  MySQL: $mysql_state"
    echo "  Redis: $redis_state"
    echo "  Kafka: $kafka_state"
    echo ""
    
    # Check that all are in failure states
    if [[ "$mysql_state" =~ ^(RETRYING|FAILED|DISCONNECTED)$ ]] && \
       [[ "$redis_state" =~ ^(RETRYING|FAILED|DISCONNECTED)$ ]] && \
       [[ "$kafka_state" =~ ^(RETRYING|FAILED|DISCONNECTED)$ ]]; then
        echo -e "${GREEN}✓ PASSED${NC}: App started successfully with all dependencies unavailable!"
        echo "This proves the application is resilient and can start without dependencies."
        exit_code=0
    else
        echo -e "${RED}✗ FAILED${NC}: Expected all dependencies to be in failure states"
        exit_code=1
    fi
else
    echo -e "${RED}✗ FAILED${NC}: App failed to start within timeout"
    echo "Showing backend logs:"
    docker logs sys-backend-no-deps --tail 100
    exit_code=1
fi

# Cleanup
echo ""
echo -e "${YELLOW}Cleaning up...${NC}"
docker compose -f docker-compose.no-deps.yml down -v

exit $exit_code
