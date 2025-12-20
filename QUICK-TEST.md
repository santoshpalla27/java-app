# Quick Test Guide

## Quick Start

### Option 1: Automated Testing (Recommended)

**Windows (PowerShell)**:
```powershell
.\test-architectures.ps1
```

**Linux/Mac (Bash)**:
```bash
chmod +x test-architectures.sh
./test-architectures.sh
```

This will automatically run all test scenarios and generate a report.

---

### Option 2: Manual Testing

#### Test 1: Standard Setup
```bash
docker-compose up --build
curl http://localhost:8080/internal/connectivity | jq
```

**Expected**: All dependencies `CONNECTED`

---

#### Test 2: No Dependencies (Resilience Test)
```bash
docker-compose -f docker-compose.no-deps.yml up --build
curl http://localhost:8080/internal/connectivity | jq
```

**Expected**: 
- App starts successfully (doesn't crash)
- All dependencies in `RETRYING`, `FAILED`, or `DISCONNECTED` state

---

#### Test 3: MySQL High Availability
```bash
docker-compose -f docker-compose.mysql-ha.yml up --build
curl http://localhost:8080/internal/connectivity | jq '.mysql'
```

**Expected**: MySQL `CONNECTED` (connects to primary)

---

#### Test 4: Kafka Multi-Broker
```bash
docker-compose -f docker-compose.kafka-cluster.yml up --build
curl http://localhost:8080/internal/connectivity | jq '.kafka.metadata.brokerCount'
```

**Expected**: `brokerCount: 3`

---

## Available Test Files

| File | Purpose |
|------|---------|
| `docker-compose.yml` | Standard local development |
| `docker-compose.no-deps.yml` | Zero dependencies (resilience test) |
| `docker-compose.mysql-ha.yml` | MySQL primary-replica setup |
| `docker-compose.redis-cluster.yml` | Redis cluster (6 nodes) |
| `docker-compose.kafka-cluster.yml` | Kafka 3-broker cluster |

---

## Cleanup

```bash
# Stop all test containers
docker-compose down -v
docker-compose -f docker-compose.no-deps.yml down -v
docker-compose -f docker-compose.mysql-ha.yml down -v
docker-compose -f docker-compose.redis-cluster.yml down -v
docker-compose -f docker-compose.kafka-cluster.yml down -v
```

---

## Success Criteria

✅ App starts in all scenarios  
✅ Connection states match infrastructure  
✅ No crashes when dependencies unavailable  
✅ Metadata reflects actual configuration (broker count, cluster mode, etc.)

For detailed testing instructions, see [TESTING.md](./TESTING.md)
