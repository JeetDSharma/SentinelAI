# Engineering journal

Meaningful surprises and how they were resolved. Newest first.

## 2026-11-01 — Phase 1: Foundation
- **Port clash:** a locally installed Postgres already listens on 5432. Compose maps the container to host **5433**; `application.properties` points there.
- **Boot 4 modular starters:** Flyway auto-configuration moved to `spring-boot-flyway`, so `flyway-core` alone does nothing — `spring-boot-starter-flyway` is required (plus `flyway-database-postgresql`). Test slices also have their own starters (`spring-boot-starter-webmvc-test`, `-data-jpa-test`).
- **Boot 4 Elasticsearch client:** the starter pulls `elasticsearch-java` 9.4.8 with the new `elasticsearch-rest5-client` transport; server image pinned to the same 9.4.8.
- **Testcontainers 2.0:** artifact names gained a `testcontainers-` prefix and `PostgreSQLContainer` moved to `org.testcontainers.postgresql` (old class still present but deprecated).
- **Boot 4 health API:** `HealthEndpoint` / `Status` moved to `org.springframework.boot.health.*` packages; verified with `javap` before use.
- **Harmless Maven warning:** "Could not transfer metadata … flyway-community-db-support … 401" — a GitHub Packages repository declared in Flyway's POM; resolution falls back to Maven Central.
- **Verified:** `./mvnw verify` → `SentinelAiApplicationIT` 2/2 pass (Flyway applied V1 on Postgres 18.6; db + elasticsearch health UP). Manual run against Compose: `/actuator/health` UP, also through the Vite proxy on :5173.
