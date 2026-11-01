# Decisions

Short records of consequential choices. Format: decision · reason · trade-off.

### D1. Modular monolith, package-by-feature
**Reason:** One deployable, easy to run and explain; feature packages (`screening`, `search`, `scoring`, `review`) keep boundaries visible.
**Trade-off:** No independent scaling — irrelevant at demo scale.

### D2. Spring Boot 4.1.2, compile target Java 21
**Reason:** Current supported Boot line. Java 21 is the LTS most job descriptions name; the local JDK 25 compiles it with `--release 21`.
**Trade-off:** Can't use Java 22–25 language features. Boot 4 brings Jackson 3 and modular starters, so some online Boot 3 examples need adjusting.

### D3. Elasticsearch for the watchlist, PostgreSQL for cases/decisions
**Reason:** Each store does what it is good at — fuzzy retrieval vs. transactional, constrained writes. Watchlist lives only in ES, so no DB→index synchronization.
**Trade-off:** Two stateful services to run (handled by Docker Compose).

### D4. Official Elasticsearch Java API client (not Spring Data Elasticsearch)
**Reason:** Explicit, readable query construction; Boot auto-configures the `ElasticsearchClient` bean.
**Trade-off:** No repository abstraction — we write queries by hand (which we want to explain anyway).

### D5. Flyway owns the schema; Hibernate `ddl-auto=validate`
**Reason:** Reviewed, versioned SQL; app fails fast if entities drift from the schema.
**Trade-off:** Every schema change needs a migration file.

### D6. Append-only `review_decision` + status on `case_candidate`
**Reason:** Current state is cheap to read; full history preserved for audit. Makes the decision write a genuine multi-statement transaction.
**Trade-off:** Two writes per decision instead of one.

### D7. Testcontainers for integration tests
**Reason:** Search queries, migrations and transactions behave differently against mocks/H2. Same images as Compose.
**Trade-off:** Tests need Docker and take ~20 s longer.

### D8. Mantine for UI
**Reason:** Mature component set (tables, forms, badges, drawers) with a professional default look — less custom CSS.
**Trade-off:** One more frontend dependency (~35 kB gzipped CSS).

### D9. Elasticsearch security disabled locally
**Reason:** Removes cert/password setup from the demo path.
**Trade-off:** Only acceptable on localhost; documented in `docker-compose.yml`.
