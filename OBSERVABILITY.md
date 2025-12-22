# Observability Architecture

## Overview

This project implements **SRE-grade observability** using OpenTelemetry, Prometheus, Grafana, and Elasticsearch. The architecture follows industry best practices for production monitoring.

---

## Architecture Diagram

```
┌─────────────┐
│   Backend   │ (Spring Boot + Micrometer)
│  Java App   │
└──────┬──────┘
       │
       ├─ Metrics (OTLP/HTTP) ──────────┐
       ├─ Traces (OTLP/gRPC) ───────────┼──→ ┌──────────────────┐
       └─ Logs (stdout JSON) ──┐        │    │ OTel Collector   │
                                │        │    └────────┬─────────┘
                                │        │             │
                         ┌──────▼────┐   │    ┌────────▼────────┐
                         │  Filebeat │───┘    │   Prometheus    │
                         └──────┬────┘        │  (+ Alerts)     │
                                │             └────────┬─────────┘
                         ┌──────▼────────┐            │
                         │ Elasticsearch │            │
                         └──────┬────────┘            │
                                │                     │
                         ┌──────▼────────┐    ┌───────▼─────────┐
                         │    Kibana     │    │    Grafana      │
                         │  (Log Search) │    │  (Dashboards)   │
                         └───────────────┘    └─────────────────┘
```

---

## Design Decisions

### 1. Metrics: Micrometer (not OpenTelemetry Meter API)

**WHY:**
- ✅ Native Spring Boot integration
- ✅ Proven compatibility with Micrometer OTLP registry
- ✅ Industry standard for JVM applications
- ✅ Better cardinality control
- ❌ OpenTelemetry Meter API had export issues with Micrometer bridge

**Metrics Exposed:**
- `dependency_state{dependency}` - Health (1=UP, 0=DOWN)
- `dependency_latency_ms{dependency}` - Connection latency
- `dependency_retry_total{dependency}` - Retry attempts
- `dependency_failure_total{dependency}` - Failure count
- `dependency_recovery_seconds{dependency}` - Recovery time

### 2. Logging: Filebeat (not OpenTelemetry Logback Appender)

**WHY:**
- ✅ Battle-tested in production
- ✅ Works with any log format
- ✅ Simpler than OTel Logback appender
- ✅ Industry standard for log collection
- ❌ OTel Logback appender had compatibility issues

**Log Path:**
```
Backend → Console (JSON) → Docker stdout → Filebeat → Elasticsearch → Kibana
```

**Log Format:**
```json
{
  "@timestamp": "2025-12-22T11:30:45.123Z",
  "level": "ERROR",
  "message": "MySQL connection failed",
  "trace_id": "a1b2c3d4e5f6g7h8",
  "span_id": "1234567890abcdef",
  "service": "system-behavior-platform"
}
```

### 3. Tracing: OpenTelemetry SDK (Auto-Instrumentation)

**WHY:**
- ✅ Spring Boot auto-instrumentation handles HTTP, JDBC, Redis
- ✅ W3C Trace Context propagation for Kafka
- ✅ No manual span creation needed (removed for simplicity)
- ✅ Traces flow to Tempo via OTel Collector

### 4. Dashboards: System-Centric (not Component-Centric)

**OLD Approach (Component-Centric):**
- ❌ "MySQL Status" panel
- ❌ "Redis Status" panel
- ❌ "Kafka Status" panel
- ❌ Doesn't answer: "Is the system usable?"

**NEW Approach (System-Centric):**
- ✅ "System Health" - % dependencies UP
- ✅ "Dependency Health" - All states in one view
- ✅ "Correlation Panels" - Latency vs Errors
- ✅ Answers: "Can users use the system right now?"

### 5. Alerts: Minimal but Actionable

**WHY 3 alerts (not 10+):**
- ✅ `DependencyDown` - Critical, actionable
- ✅ `RetryStorm` - Leading indicator
- ✅ `DependencyHighLatency` - Early warning
- ❌ More alerts = alert fatigue

Each alert has:
- Clear **severity** (critical/warning)
- **Runbook** with next steps
- **WHY** it matters

---

## Metrics Philosophy: RED Methodology

We follow **RED (Rate, Errors, Duration)** for dependency monitoring:

| Metric | Type | Purpose |
|--------|------|---------|
| `dependency_state` | Gauge | **USE** - Is it UP? |
| `dependency_latency_ms` | Gauge | **Duration** - How fast? |
| `dependency_retry_total` | Counter | **Errors** - How many retries? |
| `dependency_failure_total` | Counter | **Errors** - How many failures? |
| `dependency_recovery_seconds` | Histogram | **Duration** - How long to recover? |

---

## Correlation: The Power of Observability

### Metric → Trace → Log Flow

1. **Grafana Dashboard** shows high latency for MySQL
2. Click **"View Traces"** → Opens Tempo with MySQL traces
3. Copy `trace_id` from slow trace
4. **Kibana** search: `trace_id:a1b2c3d4e5f6g7h8`
5. See **all logs** from that request across all services

This is the **magic** of full correlation.

---

## Security & Production Readiness

### ⚠️ IMPORTANT: This is a DEMO Setup

**Current State:**
- ❌ Grafana has **no authentication** (anonymous access)
- ❌ Kibana has **no authentication**
- ❌ Prometheus is **publicly accessible**
- ❌ No TLS/HTTPS

**WHY:**
- This is intentional for **demo/development purposes**
- Simplifies local testing and showcases observability features
- **NOT suitable for production**

**For Production:**
```yaml
# Add to docker-compose.yml
grafana:
  environment:
    - GF_AUTH_ANONYMOUS_ENABLED=false
    - GF_SECURITY_ADMIN_PASSWORD=${GRAFANA_PASSWORD}
    - GF_SERVER_PROTOCOL=https
    - GF_SERVER_CERT_FILE=/etc/grafana/cert.pem
    - GF_SERVER_CERT_KEY=/etc/grafana/key.pem

kibana:
  environment:
    - ELASTICSEARCH_USERNAME=elastic
    - ELASTICSEARCH_PASSWORD=${ELASTIC_PASSWORD}
    - SERVER_SSL_ENABLED=true
```

**Production Checklist:**
- [ ] Enable authentication on all services
- [ ] Use TLS/HTTPS for all endpoints
- [ ] Deploy behind private network/VPN
- [ ] Use secrets management (not environment variables)
- [ ] Enable audit logging
- [ ] Set up backup/retention policies

---

## Quick Start

### 1. Deploy Stack
```bash
docker compose up -d
```

### 2. Access Services
- **Grafana:** http://localhost:3000 (Dashboards)
- **Prometheus:** http://localhost:9090 (Metrics)
- **Kibana:** http://localhost:5601 (Logs)
- **Backend:** http://localhost:8080 (Application)

### 3. View System Health
1. Open Grafana → "System Health" dashboard
2. Check "Dependency Health" panel
3. All should show **UP** (green)

### 4. Test Failure Scenario
```bash
# Trigger MySQL failure
curl -X POST http://localhost:8080/api/chaos/kill-mysql

# Wait 30 seconds, observe:
# - Grafana: dependency_state drops to 0
# - Prometheus: Alert fires (DependencyDown)
# - Kibana: Error logs appear

# Restore
curl -X POST http://localhost:8080/api/chaos/restore-mysql
```

### 5. Explore Logs
1. Kibana → Discover
2. Index pattern: `docker-logs-*`
3. Search: `severity_text:ERROR`
4. See logs with `trace_id` for correlation

---

## Monitoring Best Practices

### 1. Use Metrics for Alerting
- ✅ Alerts on `dependency_state == 0`
- ❌ Don't alert on logs

### 2. Use Logs for Debugging
- ✅ Search logs by `trace_id` to debug specific requests
- ❌ Don't use logs for dashboards

### 3. Use Traces for Performance
- ✅ Identify slow database queries
- ❌ Don't use traces for alerting

### 4. Dashboard Design
- ✅ Answer "Is the system usable?"
- ✅ Show trends, not just current state
- ❌ Don't create 50 panels

### 5. Alert Design
- ✅ Every alert must be actionable
- ✅ Include runbook in annotation
- ❌ Don't alert on everything

---

## Troubleshooting

### No Metrics in Grafana
```bash
# Check Prometheus targets
curl http://localhost:9090/api/v1/targets

# Check OTel Collector
curl http://localhost:8889/metrics | grep dependency

# Check backend logs
docker logs sys-backend --tail 50
```

### No Logs in Kibana
```bash
# Check Filebeat
docker logs sys-filebeat --tail 20

# Check Elasticsearch indices
curl http://localhost:9200/_cat/indices?v

# Verify logs are JSON
docker logs sys-backend --tail 5
```

### Alerts Not Firing
```bash
# Check Prometheus rules
curl http://localhost:9090/api/v1/rules

# Manually trigger failure
curl -X POST http://localhost:8080/api/chaos/kill-mysql

# Wait 1 minute, check alerts
curl http://localhost:9090/api/v1/alerts
```

---

## Architecture Evolution

### What We Changed (SRE Refactoring)

**Before:**
- OpenTelemetry Meter API (broken export)
- OTel Logback appender (compatibility issues)
- Manual span creation (unnecessary)
- Component-centric dashboards
- No alerts

**After:**
- Micrometer (proven, works)
- Filebeat (industry standard)
- Auto-instrumentation only
- System-centric dashboards
- Production-ready alerts

**WHY:**
- Fix what's broken (metrics export)
- Remove what's redundant (manual tracing)
- Simplify what's complex (logging pipeline)
- Focus on what matters (system health, not component status)

---

## References

- [RED Methodology](https://www.weave.works/blog/the-red-method-key-metrics-for-microservices-architecture/)
- [USE Method](https://www.brendangregg.com/usemethod.html)
- [OpenTelemetry Best Practices](https://opentelemetry.io/docs/concepts/observability-primer/)
- [Prometheus Alerting Best Practices](https://prometheus.io/docs/practices/alerting/)
- [Grafana Dashboard Best Practices](https://grafana.com/docs/grafana/latest/dashboards/build-dashboards/best-practices/)

---

## License

This is a demonstration project showcasing SRE-grade observability practices.
