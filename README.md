# 🚀 Real-Time Event Analytics Platform

A distributed, production-grade data pipeline that ingests, processes, and visualizes high-volume user/system events in real time — built with **Java 17**, **Spring Boot 3**, **Apache Kafka**, **PostgreSQL**, **Redis**, and **Elasticsearch**.

> Modelled after real-world systems at companies like Swiggy, Flipkart, and Razorpay.

---

## 🏗️ Architecture

```
┌──────────────────────────────────────────────────────┐
│                                                      │
│   REST Clients / Postman / curl                      │
│                                                      │
└──────────────────┬───────────────────────────────────┘
                   │  HTTP POST /api/v1/events
                   ▼
┌─────────────────────────────────┐
│   event-producer-service :8081  │  Spring Boot REST API
│   • Validates incoming events   │  • Idempotent event IDs
│   • Routes to Kafka topics      │  • Partition by userId
└──────────────────┬──────────────┘
                   │  Kafka Publish (ACKS=all)
                   ▼
┌─────────────────────────────────┐
│   Apache Kafka                  │  Topics: user-events, system-events
│   3 partitions per topic        │  Dead Letter: dead-letter-events
└──────────────────┬──────────────┘
                   │  Kafka Consume (Manual ACK)
                   ▼
┌─────────────────────────────────┐
│   event-consumer-service :8082  │  Spring Boot + Kafka Consumer
│   • Idempotency check           │  • Circuit Breaker (Resilience4j)
│   • Dual-write pattern          │  • Dead Letter Queue
└──────┬────────────────┬─────────┘
       │                │
       ▼                ▼
┌────────────┐  ┌───────────────┐  ┌──────────────────┐
│ PostgreSQL │  │Elasticsearch  │  │  Redis           │
│ (raw data) │  │(search/index) │  │  (counters/rate) │
└────────────┘  └───────────────┘  └──────────────────┘
       │                │                  │
       └────────────────┴──────────────────┘
                        │
                        ▼
┌─────────────────────────────────┐
│   analytics-api-service :8083   │  Spring Boot REST API
│   • Redis-first (sub-10ms)      │  • ES search
│   • DB for historical queries   │  • Real-time rate metrics
└─────────────────────────────────┘
```

---

## ✅ Prerequisites — Install These First

### 1. Install Docker Desktop

Docker is the only thing you need on your machine. It handles Java, Maven, Kafka, PostgreSQL, Redis, and Elasticsearch automatically.

**macOS:**
```bash
# Using Homebrew
brew install --cask docker

# OR download from:
# https://www.docker.com/products/docker-desktop/
```

**Windows:**
```
Download and install from: https://www.docker.com/products/docker-desktop/
Enable WSL2 integration when prompted.
```

**Linux (Ubuntu/Debian):**
```bash
sudo apt-get update
sudo apt-get install -y docker.io docker-compose
sudo usermod -aG docker $USER
newgrp docker
```

**Verify installation:**
```bash
docker --version           # Should show Docker version 24+
docker compose version     # Should show Docker Compose version 2+
```

> ⚠️ **Important for Docker Desktop users:** Open Docker Desktop and go to:
> Settings → Resources → Memory → Set to at least **6 GB**
> (Elasticsearch requires significant memory)

---

## 🚀 Running the Project (Complete Fresh Setup)

### Step 1 — Clone / Navigate to project
```bash
cd real-time-analytics-platform
```

### Step 2 — Start everything with one command
```bash
docker compose up --build
```

This will:
- Pull all images (Kafka, PostgreSQL, Redis, Elasticsearch, Kibana)
- Build all 3 Spring Boot services from source (no Java needed on your machine!)
- Create Kafka topics, initialize the database schema
- Start all services

> ⏳ **First run takes 5–10 minutes** — Maven downloads dependencies inside containers.
> Subsequent runs are much faster.

### Step 3 — Verify all services are healthy

Open a new terminal:
```bash
docker compose ps
```

You should see all containers as `healthy` or `running`:
```
NAME                STATUS
zookeeper           running
kafka               healthy
kafka-ui            running
postgres            healthy
redis               healthy
elasticsearch       healthy
kibana              running
event-producer      running
event-consumer      running
analytics-api       running
```

### Step 4 — Send your first event!
```bash
curl -X POST http://localhost:8081/api/v1/events \
  -H "Content-Type: application/json" \
  -d '{
    "eventType": "PURCHASE",
    "userId": "user_101",
    "sessionId": "sess_abc",
    "payload": {
      "amount": 1299.99,
      "currency": "INR",
      "items": 3
    },
    "source": "mobile-android",
    "region": "IN"
  }'
```

Expected response:
```json
{
  "status": "accepted",
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "message": "Event queued for processing"
}
```

---

## 📡 All Available APIs

### Event Producer (Port 8081)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/events` | Send a single event |
| POST | `/api/v1/events/batch` | Send up to 100 events at once |
| POST | `/api/v1/events/simulate?count=100` | Generate synthetic test data |

#### Send a batch of events:
```bash
curl -X POST http://localhost:8081/api/v1/events/batch \
  -H "Content-Type: application/json" \
  -d '[
    {"eventType":"PAGE_VIEW","userId":"user_101","payload":{"page":"/home"},"region":"IN"},
    {"eventType":"LOGIN","userId":"user_202","payload":{"method":"google"},"region":"US"},
    {"eventType":"ERROR","payload":{"code":"500","msg":"DB timeout"},"source":"web"}
  ]'
```

#### Simulate 200 random events (great for testing):
```bash
curl -X POST "http://localhost:8081/api/v1/events/simulate?count=200"
```

---

### Analytics API (Port 8083)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/analytics/dashboard` | Full dashboard summary (Redis-cached) |
| GET | `/api/v1/analytics/rate` | Real-time event rate (1/5/15/60 min) |
| GET | `/api/v1/analytics/events/by-type/{type}` | Events by type (paginated) |
| GET | `/api/v1/analytics/events/by-user/{userId}` | Events by user (paginated) |
| GET | `/api/v1/analytics/distribution/event-types` | Event type distribution |
| GET | `/api/v1/analytics/distribution/regions` | Regional distribution |
| GET | `/api/v1/analytics/search/by-type/{type}` | Elasticsearch search by type |
| GET | `/api/v1/analytics/search/by-user/{userId}` | Elasticsearch search by user |
| GET | `/api/v1/analytics/search/by-region/{region}` | Elasticsearch search by region |

#### Get dashboard (try this after simulating events):
```bash
curl http://localhost:8083/api/v1/analytics/dashboard | python3 -m json.tool
```

#### Get real-time event rate:
```bash
curl http://localhost:8083/api/v1/analytics/rate
```

#### Get all PURCHASE events (paginated):
```bash
curl "http://localhost:8083/api/v1/analytics/events/by-type/PURCHASE?page=0&size=10"
```

#### Date range query:
```bash
curl "http://localhost:8083/api/v1/analytics/events/date-range?from=2024-01-01T00:00:00Z&to=2025-12-31T23:59:59Z&page=0&size=20"
```

---

## 🖥️ Monitoring UIs

| Service | URL | Credentials |
|---------|-----|-------------|
| **Kafka UI** | http://localhost:8090 | None (open) |
| **Kibana** (Elasticsearch) | http://localhost:5601 | None (open) |
| **Producer Health** | http://localhost:8081/actuator/health | None |
| **Consumer Health** | http://localhost:8082/actuator/health | None |
| **API Health** | http://localhost:8083/actuator/health | None |

### Kafka UI — See your events flowing in real time
1. Go to http://localhost:8090
2. Click **Topics** → `user-events`
3. Click **Messages** tab
4. Send events and watch them appear!

### Kibana — Visualize analytics
1. Go to http://localhost:5601
2. Go to **Management** → **Stack Management** → **Index Patterns**
3. Create index pattern: `analytics-events`
4. Go to **Discover** to see all indexed events
5. Go to **Dashboard** to build visualizations

---

## 🧪 Testing the Key SDE-2 Features

### 1. Test Idempotency (Duplicate Prevention)
```bash
# Send same eventId twice - second one should be silently skipped
curl -X POST http://localhost:8081/api/v1/events \
  -H "Content-Type: application/json" \
  -d '{"eventId":"FIXED-ID-123","eventType":"LOGIN","userId":"user_1","payload":{"method":"otp"}}'

# Send same eventId again
curl -X POST http://localhost:8081/api/v1/events \
  -H "Content-Type: application/json" \
  -d '{"eventId":"FIXED-ID-123","eventType":"LOGIN","userId":"user_1","payload":{"method":"otp"}}'

# Check DB — should still be just 1 record
curl "http://localhost:8083/api/v1/analytics/events/by-user/user_1"
```

### 2. Test Kafka Partitioning
```bash
# Watch Kafka UI at http://localhost:8090 → Topics → user-events → Partitions
# Events with same userId always go to same partition (ordering guarantee)
curl -X POST http://localhost:8081/api/v1/events/simulate?count=50
```

### 3. Test the Dead Letter Queue
```bash
# DLQ events are stored in the dead_letter_events DB table
# Connect to PostgreSQL to check:
docker exec -it postgres psql -U analytics -d analyticsdb \
  -c "SELECT * FROM dead_letter_events LIMIT 10;"
```

### 4. Test Redis Speed (Sub-10ms)
```bash
# Dashboard endpoint reads from Redis (not DB) - should respond in < 10ms
time curl http://localhost:8083/api/v1/analytics/dashboard
```

### 5. Scale Consumer Horizontally
```bash
# Stop existing consumer
docker compose stop event-consumer

# Start 3 consumer instances (each handles 1 Kafka partition)
docker compose up --scale event-consumer=3 -d event-consumer
```

---

## 🗄️ Database Access

```bash
# Connect to PostgreSQL directly
docker exec -it postgres psql -U analytics -d analyticsdb

# Useful queries:
SELECT event_type, COUNT(*) FROM events GROUP BY event_type ORDER BY count DESC;
SELECT region, COUNT(*) FROM events GROUP BY region ORDER BY count DESC;
SELECT * FROM events ORDER BY created_at DESC LIMIT 10;
SELECT * FROM dead_letter_events;
```

## 📊 Redis Access

```bash
# Connect to Redis CLI
docker exec -it redis redis-cli

# Useful commands:
KEYS count:*           # See all event counters
GET count:event:TOTAL  # Total events processed
GET count:event:PURCHASE
KEYS rate:*            # Per-minute event rate buckets
KEYS processed:*       # Idempotency keys (TTL 24h)
```

---

## 🛑 Stopping the Project

```bash
# Stop all services (keeps data)
docker compose down

# Stop AND remove all data volumes (clean slate)
docker compose down -v
```

---

## 🔧 Troubleshooting

**"elasticsearch" container keeps restarting:**
```bash
# Increase Docker memory to 6GB in Docker Desktop settings
# OR reduce ES heap:
# In docker-compose.yml change: ES_JAVA_OPTS=-Xms256m -Xmx256m
```

**Spring Boot services show "Connection refused" on first start:**
```bash
# This is normal! Services auto-restart until Kafka/Postgres are ready.
# Wait 2-3 minutes and check again:
docker compose ps
docker compose logs event-consumer --tail=20
```

**Port already in use:**
```bash
# Check which process uses the port (e.g. 5432)
lsof -i :5432
# Kill it or change the port in docker-compose.yml
```

**Full reset:**
```bash
docker compose down -v
docker system prune -f
docker compose up --build
```

---

## 📁 Project Structure

```
real-time-analytics-platform/
├── docker-compose.yml              # Orchestrates all services
├── init-db.sql                     # PostgreSQL schema
│
├── event-producer-service/         # Port 8081 — REST API + Kafka Publisher
│   ├── src/main/java/com/analytics/producer/
│   │   ├── controller/             # REST endpoints
│   │   ├── model/                  # AnalyticsEvent model
│   │   └── config/                 # Kafka producer config + publisher service
│   └── Dockerfile
│
├── event-consumer-service/         # Port 8082 — Kafka Consumer + Dual-Writer
│   ├── src/main/java/com/analytics/consumer/
│   │   ├── consumer/               # Kafka listener (manual ACK)
│   │   ├── service/                # Core processing (idempotency, circuit breaker)
│   │   ├── model/                  # JPA entity + ES document + DLQ entity
│   │   └── repository/             # PostgreSQL + Elasticsearch repos
│   └── Dockerfile
│
├── analytics-api-service/          # Port 8083 — Query API (Redis + ES + DB)
│   ├── src/main/java/com/analytics/api/
│   │   ├── controller/             # REST endpoints
│   │   ├── service/                # Redis-first query logic
│   │   └── repository/             # JPA + ES repos
│   └── Dockerfile
│
└── k8s/
    └── deployments.yaml            # K8s Deployments + Services + Secrets
```

---

## 💡 Key Design Decisions (for Interviews)

| Decision | Why |
|----------|-----|
| **Manual Kafka ACK** | Offset committed only after successful processing → zero message loss |
| **Idempotency (Redis + DB)** | Prevents duplicate DB writes if consumer crashes mid-processing |
| **Dual-write to PG + ES** | PostgreSQL for durable storage, ES for fast search/analytics |
| **Redis counters** | O(1) reads for dashboard instead of expensive `COUNT(*)` DB queries |
| **Circuit Breaker** | Consumer keeps running even if PostgreSQL or ES goes down temporarily |
| **Dead Letter Queue** | Poison messages are stored for inspection instead of crashing consumer |
| **Partition key = userId** | Events from same user always processed in order on same partition |
| **3 partitions = 3 consumers** | Maximum horizontal scaling for this setup |

---
