# Architecture Testing Script (PowerShell)
# Tests connection blueprints against different infrastructure configurations

$ErrorActionPreference = "Stop"

Write-Host "======================================" -ForegroundColor Cyan
Write-Host "Connection Blueprint Architecture Tests" -ForegroundColor Cyan
Write-Host "======================================" -ForegroundColor Cyan
Write-Host ""

# Test results
$PASSED = 0
$FAILED = 0

# Function to wait for app startup
function Wait-ForApp {
    Write-Host "Waiting for app to start..." -ForegroundColor Yellow
    for ($i = 1; $i -le 60; $i++) {
        try {
            $response = Invoke-WebRequest -Uri "http://localhost:8080/actuator/health" -UseBasicParsing -TimeoutSec 2 -ErrorAction SilentlyContinue
            if ($response.StatusCode -eq 200) {
                Write-Host "App is up!" -ForegroundColor Green
                return $true
            }
        } catch {
            # Ignore errors, keep trying
        }
        Start-Sleep -Seconds 2
    }
    Write-Host "App failed to start within timeout" -ForegroundColor Red
    return $false
}

# Function to check connectivity endpoint
function Test-Connectivity {
    param(
        [string]$ExpectedMySQL,
        [string]$ExpectedRedis,
        [string]$ExpectedKafka
    )
    
    try {
        $response = Invoke-RestMethod -Uri "http://localhost:8080/internal/connectivity" -UseBasicParsing
        
        $mysqlState = $response.mysql.state
        $redisState = $response.redis.state
        $kafkaState = $response.kafka.state
        
        Write-Host "  MySQL: $mysqlState (expected: $ExpectedMySQL)" -ForegroundColor White
        Write-Host "  Redis: $redisState (expected: $ExpectedRedis)" -ForegroundColor White
        Write-Host "  Kafka: $kafkaState (expected: $ExpectedKafka)" -ForegroundColor White
        
        if ($mysqlState -eq $ExpectedMySQL -and $redisState -eq $ExpectedRedis -and $kafkaState -eq $ExpectedKafka) {
            return $true
        } else {
            return $false
        }
    } catch {
        Write-Host "  Error checking connectivity: $_" -ForegroundColor Red
        return $false
    }
}

# Function to run a test
function Invoke-Test {
    param(
        [string]$TestName,
        [string]$ComposeFile,
        [string]$ExpectedMySQL,
        [string]$ExpectedRedis,
        [string]$ExpectedKafka
    )
    
    Write-Host ""
    Write-Host "======================================" -ForegroundColor Cyan
    Write-Host "Test: $TestName" -ForegroundColor Cyan
    Write-Host "======================================" -ForegroundColor Cyan
    
    # Start services
    Write-Host "Starting services with $ComposeFile..." -ForegroundColor Yellow
    docker-compose -f $ComposeFile up -d --build
    
    # Wait for app
    if (Wait-ForApp) {
        # Give it a bit more time for health checks
        Start-Sleep -Seconds 15
        
        # Check connectivity
        Write-Host "Checking connectivity states..." -ForegroundColor Yellow
        if (Test-Connectivity -ExpectedMySQL $ExpectedMySQL -ExpectedRedis $ExpectedRedis -ExpectedKafka $ExpectedKafka) {
            Write-Host "✓ PASSED: $TestName" -ForegroundColor Green
            $script:PASSED++
        } else {
            Write-Host "✗ FAILED: $TestName - States don't match expected" -ForegroundColor Red
            $script:FAILED++
        }
    } else {
        Write-Host "✗ FAILED: $TestName - App failed to start" -ForegroundColor Red
        $script:FAILED++
    }
    
    # Cleanup
    Write-Host "Cleaning up..." -ForegroundColor Yellow
    docker-compose -f $ComposeFile down -v | Out-Null
    Start-Sleep -Seconds 5
}

# Test 1: Standard Local Setup
Invoke-Test -TestName "Standard Local Setup" `
    -ComposeFile "docker-compose.yml" `
    -ExpectedMySQL "CONNECTED" `
    -ExpectedRedis "CONNECTED" `
    -ExpectedKafka "CONNECTED"

# Test 2: MySQL High Availability
Invoke-Test -TestName "MySQL High Availability" `
    -ComposeFile "docker-compose.mysql-ha.yml" `
    -ExpectedMySQL "CONNECTED" `
    -ExpectedRedis "CONNECTED" `
    -ExpectedKafka "CONNECTED"

# Test 3: Kafka Multi-Broker
Invoke-Test -TestName "Kafka Multi-Broker Cluster" `
    -ComposeFile "docker-compose.kafka-cluster.yml" `
    -ExpectedMySQL "CONNECTED" `
    -ExpectedRedis "CONNECTED" `
    -ExpectedKafka "CONNECTED"

# Test 4: No Dependencies (Resilience)
Write-Host ""
Write-Host "======================================" -ForegroundColor Cyan
Write-Host "Test: No Dependencies (Resilience)" -ForegroundColor Cyan
Write-Host "======================================" -ForegroundColor Cyan
Write-Host "Starting backend with no dependencies..." -ForegroundColor Yellow
docker-compose -f docker-compose.no-deps.yml up -d --build

if (Wait-ForApp) {
    Start-Sleep -Seconds 10
    
    try {
        $response = Invoke-RestMethod -Uri "http://localhost:8080/internal/connectivity" -UseBasicParsing
        $mysqlState = $response.mysql.state
        $redisState = $response.redis.state
        $kafkaState = $response.kafka.state
        
        Write-Host "  MySQL: $mysqlState" -ForegroundColor White
        Write-Host "  Redis: $redisState" -ForegroundColor White
        Write-Host "  Kafka: $kafkaState" -ForegroundColor White
        
        # Check that all are in failure states
        $validStates = @("RETRYING", "FAILED", "DISCONNECTED")
        if ($validStates -contains $mysqlState -and $validStates -contains $redisState -and $validStates -contains $kafkaState) {
            Write-Host "✓ PASSED: No Dependencies - App started successfully with all deps unavailable" -ForegroundColor Green
            $PASSED++
        } else {
            Write-Host "✗ FAILED: No Dependencies - Unexpected states" -ForegroundColor Red
            $FAILED++
        }
    } catch {
        Write-Host "✗ FAILED: No Dependencies - Error checking connectivity: $_" -ForegroundColor Red
        $FAILED++
    }
} else {
    Write-Host "✗ FAILED: No Dependencies - App failed to start" -ForegroundColor Red
    $FAILED++
}

docker-compose -f docker-compose.no-deps.yml down -v | Out-Null

# Print summary
Write-Host ""
Write-Host "======================================" -ForegroundColor Cyan
Write-Host "Test Summary" -ForegroundColor Cyan
Write-Host "======================================" -ForegroundColor Cyan
Write-Host "Passed: $PASSED" -ForegroundColor Green
Write-Host "Failed: $FAILED" -ForegroundColor Red
Write-Host ""

if ($FAILED -eq 0) {
    Write-Host "All tests passed! ✓" -ForegroundColor Green
    exit 0
} else {
    Write-Host "Some tests failed ✗" -ForegroundColor Red
    exit 1
}
