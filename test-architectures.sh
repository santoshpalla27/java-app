#!/bin/bash

# Architecture Testing Script
# Tests connection blueprints against different infrastructure configurations

# Note: Not using 'set -e' to ensure all tests run even if one fails

echo "======================================"
echo "Connection Blueprint Architecture Tests"
echo "======================================"
echo ""

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Test results
PASSED=0
FAILED=0

# Function to ensure complete cleanup
cleanup_all() {
    echo -e "${YELLOW}Ensuring all containers are stopped...${NC}"
    docker compose -f docker-compose.yml down -v 2>/dev/null || true
    docker compose -f docker-compose.mysql-ha.yml down -v 2>/dev/null || true
    docker compose -f docker-compose.redis-cluster.yml down -v 2>/dev/null || true
    docker compose -f docker-compose.kafka-cluster.yml down -v 2>/dev/null || true
    docker compose -f docker-compose.no-deps.yml down -v 2>/dev/null || true
    
    # Wait for cleanup to complete
    echo "Waiting for cleanup to complete..."
    sleep 10
    
    # Verify no containers are running
    running=$(docker ps -q | wc -l)
    if [ $running -gt 0 ]; then
        echo -e "${YELLOW}Warning: $running containers still running${NC}"
        docker ps
    else
        echo -e "${GREEN}All containers stopped${NC}"
    fi
}

# Function to wait for app startup
wait_for_app() {
    echo -e "${BLUE}Waiting for app to start...${NC}"
    local max_attempts=60
    local attempt=0
    
    while [ $attempt -lt $max_attempts ]; do
        if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
            echo -e "${GREEN}App is up!${NC}"
            return 0
        fi
        ((attempt++))
        echo -n "."
        sleep 2
    done
    
    echo ""
    echo -e "${RED}App failed to start within timeout${NC}"
    echo "Showing backend logs:"
    docker logs sys-backend --tail 50
    return 1
}

# Function to check connectivity endpoint
check_connectivity() {
    local expected_mysql=$1
    local expected_redis=$2
    local expected_kafka=$3
    
    echo -e "${BLUE}Fetching connectivity status...${NC}"
    response=$(curl -s http://localhost:8080/internal/connectivity)
    
    if [ -z "$response" ]; then
        echo -e "${RED}Error: Empty response from connectivity endpoint${NC}"
        return 1
    fi
    
    mysql_state=$(echo $response | jq -r '.mysql.state')
    redis_state=$(echo $response | jq -r '.redis.state')
    kafka_state=$(echo $response | jq -r '.kafka.state')
    
    echo "  MySQL: $mysql_state (expected: $expected_mysql)"
    echo "  Redis: $redis_state (expected: $expected_redis)"
    echo "  Kafka: $kafka_state (expected: $expected_kafka)"
    
    # Show full response for debugging
    echo -e "${YELLOW}Full response:${NC}"
    echo $response | jq '.'
    
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
    echo -e "${BLUE}Test: $test_name${NC}"
    echo "======================================"
    
    # Ensure clean state
    echo "Cleaning up any existing containers..."
    cleanup_all
    
    # Start services
    echo -e "${BLUE}Starting services with $compose_file...${NC}"
    if ! docker compose -f $compose_file up -d --build; then
        echo -e "${RED}✗ FAILED${NC}: $test_name - Failed to start containers"
        ((FAILED++))
    else
        # Show running containers
        echo "Running containers:"
        docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
        
        # Wait for app
        if wait_for_app; then
            # Give it more time for health checks to stabilize
            echo "Waiting for health checks to stabilize..."
            sleep 20
            
            # Check connectivity
            echo -e "${BLUE}Checking connectivity states...${NC}"
            if check_connectivity "$expected_mysql" "$expected_redis" "$expected_kafka"; then
                echo -e "${GREEN}✓ PASSED${NC}: $test_name"
                ((PASSED++))
            else
                echo -e "${RED}✗ FAILED${NC}: $test_name - States don't match expected"
                ((FAILED++))
                
                # Show logs for debugging
                echo "Backend logs:"
                docker logs sys-backend --tail 100
            fi
        else
            echo -e "${RED}✗ FAILED${NC}: $test_name - App failed to start"
            ((FAILED++))
        fi
    fi
    
    # Cleanup (always runs regardless of test result)
    echo -e "${YELLOW}Cleaning up test environment...${NC}"
    docker compose -f $compose_file down -v
    
    # Wait for complete cleanup
    echo "Waiting for cleanup to complete..."
    sleep 10
    
    echo -e "${BLUE}Test completed. Moving to next test...${NC}"
    echo ""
}

# Initial cleanup
echo -e "${YELLOW}Performing initial cleanup...${NC}"
cleanup_all

# Test 1: Standard Local Setup
run_test "Standard Local Setup" \
    "docker-compose.yml" \
    "CONNECTED" \
    "CONNECTED" \
    "CONNECTED"

# Test 2: MySQL High Availability (if file exists)
if [ -f "docker-compose.mysql-ha.yml" ]; then
    run_test "MySQL High Availability" \
        "docker-compose.mysql-ha.yml" \
        "CONNECTED" \
        "CONNECTED" \
        "CONNECTED"
else
    echo -e "${YELLOW}Skipping MySQL HA test - docker-compose.mysql-ha.yml not found${NC}"
fi

# Test 3: Kafka Multi-Broker (if file exists)
if [ -f "docker-compose.kafka-cluster.yml" ]; then
    run_test "Kafka Multi-Broker Cluster" \
        "docker-compose.kafka-cluster.yml" \
        "CONNECTED" \
        "CONNECTED" \
        "CONNECTED"
else
    echo -e "${YELLOW}Skipping Kafka cluster test - docker-compose.kafka-cluster.yml not found${NC}"
fi

# Test 4: No Dependencies (Resilience)
if [ -f "docker-compose.no-deps.yml" ]; then
    echo ""
    echo "======================================"
    echo -e "${BLUE}Test: No Dependencies (Resilience)${NC}"
    echo "======================================"
    
    cleanup_all
    
    echo "Starting backend with no dependencies..."
    docker compose -f docker-compose.no-deps.yml up -d --build
    
    if wait_for_app; then
        sleep 15
        
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
    
    docker compose -f docker-compose.no-deps.yml down -v
else
    echo -e "${YELLOW}Skipping no-deps test - docker-compose.no-deps.yml not found${NC}"
fi

# Final cleanup
echo ""
echo -e "${YELLOW}Performing final cleanup...${NC}"
cleanup_all

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
