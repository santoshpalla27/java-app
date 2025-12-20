#!/bin/bash

# Architecture Testing Script
# Tests connection blueprints against different infrastructure configurations

set -e

echo "======================================"
echo "Connection Blueprint Architecture Tests"
echo "======================================"
echo ""

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Test results
PASSED=0
FAILED=0

# Function to wait for app startup
wait_for_app() {
    echo "Waiting for app to start..."
    for i in {1..60}; do
        if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
            echo "App is up!"
            return 0
        fi
        sleep 2
    done
    echo "App failed to start within timeout"
    return 1
}

# Function to check connectivity endpoint
check_connectivity() {
    local expected_mysql=$1
    local expected_redis=$2
    local expected_kafka=$3
    
    response=$(curl -s http://localhost:8080/internal/connectivity)
    
    mysql_state=$(echo $response | jq -r '.mysql.state')
    redis_state=$(echo $response | jq -r '.redis.state')
    kafka_state=$(echo $response | jq -r '.kafka.state')
    
    echo "  MySQL: $mysql_state (expected: $expected_mysql)"
    echo "  Redis: $redis_state (expected: $expected_redis)"
    echo "  Kafka: $kafka_state (expected: $expected_kafka)"
    
    if [[ "$mysql_state" == "$expected_mysql" ]] && \
       [[ "$redis_state" == "$expected_redis" ]] && \
       [[ "$kafka_state" == "$expected_kafka" ]]; then
        return 0
    else
        return 1
    fi
}

# Function to run a test
run_test() {
    local test_name=$1
    local compose_file=$2
    local expected_mysql=$3
    local expected_redis=$4
    local expected_kafka=$5
    
    echo ""
    echo "======================================"
    echo "Test: $test_name"
    echo "======================================"
    
    # Start services
    echo "Starting services with $compose_file..."
    docker-compose -f $compose_file up -d --build
    
    # Wait for app
    if wait_for_app; then
        # Give it a bit more time for health checks
        sleep 15
        
        # Check connectivity
        echo "Checking connectivity states..."
        if check_connectivity "$expected_mysql" "$expected_redis" "$expected_kafka"; then
            echo -e "${GREEN}✓ PASSED${NC}: $test_name"
            ((PASSED++))
        else
            echo -e "${RED}✗ FAILED${NC}: $test_name - States don't match expected"
            ((FAILED++))
        fi
    else
        echo -e "${RED}✗ FAILED${NC}: $test_name - App failed to start"
        ((FAILED++))
    fi
    
    # Cleanup
    echo "Cleaning up..."
    docker-compose -f $compose_file down -v > /dev/null 2>&1
    sleep 5
}

# Test 1: Standard Local Setup
run_test "Standard Local Setup" \
    "docker-compose.yml" \
    "CONNECTED" \
    "CONNECTED" \
    "CONNECTED"

# Test 2: MySQL High Availability
run_test "MySQL High Availability" \
    "docker-compose.mysql-ha.yml" \
    "CONNECTED" \
    "CONNECTED" \
    "CONNECTED"

# Test 3: Kafka Multi-Broker
run_test "Kafka Multi-Broker Cluster" \
    "docker-compose.kafka-cluster.yml" \
    "CONNECTED" \
    "CONNECTED" \
    "CONNECTED"

# Test 4: No Dependencies (Resilience)
echo ""
echo "======================================"
echo "Test: No Dependencies (Resilience)"
echo "======================================"
echo "Starting backend with no dependencies..."
docker-compose -f docker-compose.no-deps.yml up -d --build

if wait_for_app; then
    sleep 10
    
    response=$(curl -s http://localhost:8080/internal/connectivity)
    mysql_state=$(echo $response | jq -r '.mysql.state')
    redis_state=$(echo $response | jq -r '.redis.state')
    kafka_state=$(echo $response | jq -r '.kafka.state')
    
    echo "  MySQL: $mysql_state"
    echo "  Redis: $redis_state"
    echo "  Kafka: $kafka_state"
    
    # Check that all are in failure states (RETRYING, FAILED, or DISCONNECTED)
    if [[ "$mysql_state" =~ ^(RETRYING|FAILED|DISCONNECTED)$ ]] && \
       [[ "$redis_state" =~ ^(RETRYING|FAILED|DISCONNECTED)$ ]] && \
       [[ "$kafka_state" =~ ^(RETRYING|FAILED|DISCONNECTED)$ ]]; then
        echo -e "${GREEN}✓ PASSED${NC}: No Dependencies - App started successfully with all deps unavailable"
        ((PASSED++))
    else
        echo -e "${RED}✗ FAILED${NC}: No Dependencies - Unexpected states"
        ((FAILED++))
    fi
else
    echo -e "${RED}✗ FAILED${NC}: No Dependencies - App failed to start"
    ((FAILED++))
fi

docker-compose -f docker-compose.no-deps.yml down -v > /dev/null 2>&1

# Print summary
echo ""
echo "======================================"
echo "Test Summary"
echo "======================================"
echo -e "${GREEN}Passed: $PASSED${NC}"
echo -e "${RED}Failed: $FAILED${NC}"
echo ""

if [ $FAILED -eq 0 ]; then
    echo -e "${GREEN}All tests passed! ✓${NC}"
    exit 0
else
    echo -e "${RED}Some tests failed ✗${NC}"
    exit 1
fi
