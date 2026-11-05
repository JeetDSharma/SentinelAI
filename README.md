# Sentinel — Explainable Entity Screening

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

## Demo examples
Measured against the seeded synthetic data (`POST /api/screenings`). "Relevance" is Elasticsearch `_score`, shown only to contrast with the match score.

| Input | Top result | Match score | Shows |
|---|---|---|---|
| `Viktor Petrov`, PERSON, RU | WL-0001 | 80 HIGH | exact alias + country + type |
| `Viktor Petrof`, PERSON | WL-0001 | 73 MEDIUM | misspelling (fuzzy) |
| `Viktor Petrov`, PERSON, id `P-RU-7701234` | WL-0001 | 95 HIGH | identifier corroboration |
| `Jose Garcia`, PERSON | WL-0009 | 71 MEDIUM | partial name, accents (`José … García Ruiz`) |
| `Orion Global Trading Corp`, ORGANIZATION, AE | WL-0103 | 80 HIGH | abbreviation `Corp → Corporation` |
| `Li Wei`, PERSON | WL-0007 **and** WL-0008 | 75 / 75 | reversed-name ambiguity; ES relevance 68 vs 20 — why `_score` is not a match score |
| `Petrova Consulting`, PERSON | WL-0002 | 55 LOW | type mismatch penalized, not hidden |
| `Zebulon Quackenbush`, PERSON | — | — | no candidates |

Full demo script: Phase 5.

## Architecture page
Open **http://localhost:5173/#/architecture** (not linked from the workbench): system diagram, screening and review sequence diagrams, schema.

## Docs
- [Architecture](docs/ARCHITECTURE.md) · [Decisions](docs/DECISIONS.md) · [Engineering journal](docs/JOURNAL.md)
