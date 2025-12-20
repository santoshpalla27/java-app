# Architecture Testing Guide

This directory contains multiple Docker Compose configurations to test the connection blueprints against different infrastructure architectures.

## Test Scenarios

### 1. **Local Development** (`docker-compose.yml`)
Standard single-instance setup for local development.

### 2. **MySQL Multi-AZ Simulation** (`docker-compose.mysql-ha.yml`)
Tests MySQL high-availability with primary-replica setup.

### 3. **Redis Cluster** (`docker-compose.redis-cluster.yml`)
Tests Redis in cluster mode (3 masters, 3 replicas).

### 4. **Kafka Multi-Broker** (`docker-compose.kafka-cluster.yml`)
Tests Kafka with 3 brokers for production-like setup.

### 5. **All Dependencies Down** (`docker-compose.no-deps.yml`)
Tests app startup with zero dependencies available.

### 6. **Mixed Architecture** (`docker-compose.mixed.yml`)
Combines MySQL replica, Redis cluster, and Kafka multi-broker.

---

## How to Run Tests

### Test 1: Standard Local Setup
```bash
docker-compose up --build
```

**Expected**: All dependencies CONNECTED

**Verify**:
```bash
curl http://localhost:8080/internal/connectivity | jq
```

---

### Test 2: MySQL High Availability
```bash
docker-compose -f docker-compose.mysql-ha.yml up --build
```

**What This Tests**: Connection blueprint works with MySQL primary-replica setup

**Expected**: MySQL state = CONNECTED (connects to primary)

**Verify**:
```bash
curl http://localhost:8080/internal/connectivity | jq '.mysql'
```

---

### Test 3: Redis Cluster Mode
```bash
docker-compose -f docker-compose.redis-cluster.yml up --build
```

**What This Tests**: Connection blueprint works with Redis Cluster

**Expected**: Redis state = CONNECTED with `mode: cluster` in metadata

**Verify**:
```bash
curl http://localhost:8080/internal/connectivity | jq '.redis'
```

**Note**: Update `application.yml` to set `app.redis.mode: cluster` before running.

---

### Test 4: Kafka Multi-Broker Cluster
```bash
docker-compose -f docker-compose.kafka-cluster.yml up --build
```

**What This Tests**: Connection blueprint works with multi-broker Kafka

**Expected**: Kafka state = CONNECTED with `brokerCount: 3` in metadata

**Verify**:
```bash
curl http://localhost:8080/internal/connectivity | jq '.kafka.metadata.brokerCount'
```

---

### Test 5: No Dependencies (Resilience Test)
```bash
docker-compose -f docker-compose.no-deps.yml up --build
```

**What This Tests**: App starts successfully with zero dependencies

**Expected**: 
- App starts without crashing
- All dependencies in DISCONNECTED or RETRYING state
- Retry counts increasing over time

**Verify**:
```bash
# App should start
docker-compose -f docker-compose.no-deps.yml logs backend | grep "Started SystemPlatformApplication"

# Check connectivity state
curl http://localhost:8080/internal/connectivity | jq

# Wait 1 minute and check retry counts
sleep 60
curl http://localhost:8080/internal/connectivity | jq '.mysql.retryCount, .redis.retryCount, .kafka.retryCount'
```

---

### Test 6: Auto-Recovery Test

**Step 1**: Start with no dependencies
```bash
docker-compose -f docker-compose.no-deps.yml up -d
```

**Step 2**: Verify all dependencies are RETRYING/FAILED
```bash
curl http://localhost:8080/internal/connectivity | jq '.mysql.state, .redis.state, .kafka.state'
```

**Step 3**: Start MySQL in separate terminal
```bash
docker run -d --name test-mysql --network java-app_sys-net \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=sys_platform \
  -e MYSQL_USER=user \
  -e MYSQL_PASSWORD=password \
  -p 3306:3306 \
  mysql:8.0
```

**Step 4**: Wait 30 seconds and verify MySQL auto-recovery
```bash
sleep 30
curl http://localhost:8080/internal/connectivity | jq '.mysql.state'
# Expected: "CONNECTED"
```

**Step 5**: Repeat for Redis and Kafka

---

### Test 7: Degraded State Test

**Step 1**: Start with all dependencies
```bash
docker-compose up -d
```

**Step 2**: Verify all CONNECTED
```bash
curl http://localhost:8080/internal/connectivity | jq '.mysql.state, .redis.state, .kafka.state'
```

**Step 3**: Exhaust MySQL pool (requires authentication)
```bash
# Login first to get JWT token
TOKEN=$(curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq -r '.token')

# Exhaust pool
curl -X POST http://localhost:8080/api/admin/failure/db/exhaust \
  -H "Authorization: Bearer $TOKEN"
```

**Step 4**: Wait 15 seconds and check state
```bash
sleep 15
curl http://localhost:8080/internal/connectivity | jq '.mysql.state'
# Expected: "DEGRADED"
```

**Step 5**: Release pool
```bash
curl -X POST http://localhost:8080/api/admin/failure/db/release \
  -H "Authorization: Bearer $TOKEN"
```

**Step 6**: Verify recovery
```bash
sleep 15
curl http://localhost:8080/internal/connectivity | jq '.mysql.state'
# Expected: "CONNECTED"
```

---

## Quick Test Matrix

| Test Scenario | MySQL | Redis | Kafka | Expected Result |
|---------------|-------|-------|-------|-----------------|
| Local Dev | Single | Standalone | Single Broker | All CONNECTED |
| MySQL HA | Primary+Replica | Standalone | Single Broker | All CONNECTED |
| Redis Cluster | Single | Cluster (6 nodes) | Single Broker | All CONNECTED |
| Kafka Cluster | Single | Standalone | 3 Brokers | All CONNECTED |
| No Dependencies | None | None | None | All RETRYING/FAILED |
| Auto-Recovery | Delayed Start | Delayed Start | Delayed Start | RETRYING → CONNECTED |

---

## Automated Test Script

Run all tests automatically:

```bash
./test-architectures.sh
```

This script will:
1. Run each Docker Compose configuration
2. Wait for app startup
3. Query `/internal/connectivity`
4. Verify expected states
5. Generate a test report

---

## Cleanup

After testing, clean up all containers:

```bash
# Stop and remove all test containers
docker-compose down -v
docker-compose -f docker-compose.mysql-ha.yml down -v
docker-compose -f docker-compose.redis-cluster.yml down -v
docker-compose -f docker-compose.kafka-cluster.yml down -v
docker-compose -f docker-compose.no-deps.yml down -v
docker-compose -f docker-compose.mixed.yml down -v

# Remove test network
docker network prune -f
```

---

## Success Criteria

✅ App starts successfully in all scenarios  
✅ Connection states accurately reflect infrastructure  
✅ Auto-recovery works when dependencies come online  
✅ Degraded state detected during pool exhaustion  
✅ Retry counts increase during failures  
✅ No crashes or exceptions during dependency unavailability  
✅ Metadata reflects actual infrastructure (broker count, cluster mode, etc.)
