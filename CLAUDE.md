# Sentinel — agent guide

Entity-screening demo: analyst screens a name against a **synthetic** watchlist (Elasticsearch), a deterministic Java scorer explains each candidate, and analyst decisions are stored in PostgreSQL. Portfolio/interview project — optimize for clarity and demo reliability, not feature count.

## Layout
- `src/main/java/com/example/sentinelai/` — Spring Boot 4.1 modular monolith, packaged by feature:
  `screening/` (REST + orchestration), `search/` (Elasticsearch + seeding), `scoring/` (pure Java), `review/` (JPA entities, transactions)
- `src/main/resources/db/migration/` — Flyway SQL (schema source of truth)
- `frontend/` — React 19 + TypeScript + Vite + Mantine, single screen; dev proxy forwards `/api` and `/actuator` to :8080
- `docs/` — ARCHITECTURE, DECISIONS, INTERVIEW_NOTES, JOURNAL

## Commands
- Infra: `docker compose up -d --wait` (Postgres on **5433**, ES on 9200) · reset: `docker compose down -v`
- Backend: `./mvnw spring-boot:run`
- Unit tests: `./mvnw test` · all tests incl. Testcontainers `*IT`: `./mvnw verify` (needs Docker)
- Frontend: `cd frontend && npm run dev` · check: `npm run build && npm run lint`

## Invariants (do not break)
- Watchlist records live **only** in Elasticsearch. Postgres stores cases, candidate snapshots, decisions.
- Never hold a DB transaction open across an Elasticsearch or LLM call. `@Transactional` lives in `CaseReviewService`, not in the orchestrating `ScreeningService` (also avoids the self-invocation proxy trap).
- Match score (0–100, `MatchScoringService`) is separate from Elasticsearch `_score`. Never present `_score` as risk.
- Scoring is deterministic and every point is explained by a `MatchSignal`.
- `review_decision` is append-only. Candidate status changes only via `CaseReviewService.recordDecision` (version-checked).
- Controllers return DTOs built inside the service transaction; never return JPA entities.
- Schema changes = new Flyway migration (`V{n}__*.sql`); never edit an applied one. `ddl-auto=validate`.
- Seeding must be idempotent (safe to rerun).
- No real-world compliance claims; all data is synthetic.

## Conventions
- Java 21 target, constructor injection, records for DTOs/value objects, no Lombok.
- Errors as `ProblemDetail` via one `@RestControllerAdvice`.
- Boot 4 specifics: Jackson 3 (`tools.jackson.*`), modular starters (e.g. `spring-boot-starter-flyway`), Testcontainers 2 (`org.testcontainers.postgresql.PostgreSQLContainer`).
- No new major dependency without a line in `docs/DECISIONS.md`. No Kafka/Redis/K8s/microservices.

## Verification expectations
- Run the relevant tests after every change and report real output; never claim a pass without running.
- Integration tests use real containers where mocks would hide behavior (queries, transactions, migrations). Every `*IT` class is annotated with exactly `@IntegrationTest` (one shared Spring context + containers); don't add per-class properties.
- After moving/deleting classes run `./mvnw clean verify` (incremental compile can miss stale references).
- Record surprises in `docs/JOURNAL.md`, decisions in `docs/DECISIONS.md`, interview-worthy concepts in `docs/INTERVIEW_NOTES.md`.
