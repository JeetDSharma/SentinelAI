# Engineering journal

Meaningful surprises and how they were resolved. Newest first.

## 2026-11-01 — Phase 2: Screening engine
- **Scoring false positive found by test:** `differentSurnameScoresLow` failed — "John Doe" vs "John Smith" scored 53. Probed Jaro-Winkler directly: `jw("doe","john") = 0.53`, so an unrelated word earned half credit. Added a 0.80 per-word noise floor (D13) rather than loosening the test. Result: below threshold.
- **MockMvcTester is lazy:** `mvc.post()...content(...)` returns a builder; each `assertThat(builder)` re-executed the request (Mockito saw 7 calls for 7 assertions). Fixed with `.exchange()` to execute once.
- **Startup race (found manually, not by tests):** first curl run after startup got 503s, then *empty results*. `ApplicationRunner` runs after Tomcat is already accepting requests; delete→create→bulk exposed a missing/empty index for ~2 s. Fixed with build-new-index + atomic alias swap (D14). Re-verified by restarting under continuous requests: 8 × 200, 0 empty results, alias → one 60-doc index. The old concrete `watchlist` index in the Compose volume was migrated by the "concrete index has alias name" branch.
- **Verified:** `./mvnw verify` → 22 unit tests (scoring, normalizer, controller slice) + 22 integration tests (13 search scenarios, identifier-only retrieval, reversed names, type mismatch, no-match, idempotent seeding, missing index → 503, unreachable cluster → 503) all pass.

## 2026-11-01 — Phase 1: Foundation
- **Port clash:** a locally installed Postgres already listens on 5432. Compose maps the container to host **5433**; `application.properties` points there.
- **Boot 4 modular starters:** Flyway auto-configuration moved to `spring-boot-flyway`, so `flyway-core` alone does nothing — `spring-boot-starter-flyway` is required (plus `flyway-database-postgresql`). Test slices also have their own starters (`spring-boot-starter-webmvc-test`, `-data-jpa-test`).
- **Boot 4 Elasticsearch client:** the starter pulls `elasticsearch-java` 9.4.8 with the new `elasticsearch-rest5-client` transport; server image pinned to the same 9.4.8.
- **Testcontainers 2.0:** artifact names gained a `testcontainers-` prefix and `PostgreSQLContainer` moved to `org.testcontainers.postgresql` (old class still present but deprecated).
- **Boot 4 health API:** `HealthEndpoint` / `Status` moved to `org.springframework.boot.health.*` packages; verified with `javap` before use.
- **Harmless Maven warning:** "Could not transfer metadata … flyway-community-db-support … 401" — a GitHub Packages repository declared in Flyway's POM; resolution falls back to Maven Central.
- **Verified:** `./mvnw verify` → `SentinelAiApplicationIT` 2/2 pass (Flyway applied V1 on Postgres 18.6; db + elasticsearch health UP). Manual run against Compose: `/actuator/health` UP, also through the Vite proxy on :5173.
