# Sentinel — AI-Assisted Entity Screening

A compact entity-screening application: an analyst screens a person or organization against a **synthetic** watchlist, sees scored candidate matches with explicit evidence, and records review decisions.

> Portfolio project inspired by financial risk-screening problems. All data is synthetic. It does not make real regulatory or compliance decisions.

**Stack:** Java 21 · Spring Boot 4.1 · PostgreSQL 18 + Flyway · Elasticsearch 9.4 (official Java client) · React 19 + TypeScript + Vite + Mantine · Testcontainers · GitHub Actions

## Prerequisites
- JDK 21+ (Maven is provided via `./mvnw`)
- Docker (Compose v2)
- Node.js 20+

## Run locally
```bash
docker compose up -d --wait      # Postgres on localhost:5433, Elasticsearch on localhost:9200
./mvnw spring-boot:run           # backend on http://localhost:8080
cd frontend && npm install && npm run dev   # UI on http://localhost:5173
```
Health check: `curl localhost:8080/actuator/health` — `db` and `elasticsearch` should be `UP`.

## Reset
```bash
docker compose down -v           # drops all cases, decisions and the search index
```

## Tests
```bash
./mvnw test      # unit tests
./mvnw verify    # unit + integration tests (starts throwaway Postgres/Elasticsearch containers; Docker required)
cd frontend && npm run build && npm run lint
```

## Demo script
_Added in Phase 5._

## Docs
- [Architecture](docs/ARCHITECTURE.md) · [Decisions](docs/DECISIONS.md) · [Interview notes](docs/INTERVIEW_NOTES.md) · [Engineering journal](docs/JOURNAL.md)
