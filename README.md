# Real-Time System Connectivity & Behavior Platform

A production-grade, DevOps-focused observability platform that visualizes **REAL** infrastructure behavior.

## Core Features
- **Real-Time Probing**: Actively probes MySQL, Redis, and Kafka.
- **Live Topology**: React Flow graph that updates via WebSockets.
- **Chaos Engineering**: Inject failures (Latency, Kill Connections) to see real-time impact.
- **Event Streaming**: Full Kafka + WebSocket loop for event propagation.

## Tech Stack
- **Frontend**: React 18, TypeScript, Vite, Tailwind, React Flow.
- **Backend**: Java 17, Spring Boot 3, Spring Security (JWT), Data JPA.
- **Infra**: MySQL 8, Redis 7, Kafka 7.5, Docker Compose.

## Quick Start
```bash
docker-compose up --build
```
Access at [http://localhost:3000](http://localhost:3000).
Login: `admin` / `admin123`.

## Troubleshooting

### Kafka Cluster ID Mismatch
If you see `InconsistentClusterIdException` in logs, the Kafka volumes are stale. Run:
```bash
docker-compose down -v
docker-compose up --build
```
