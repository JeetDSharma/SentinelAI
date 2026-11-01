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

### D10. Elasticsearch retrieves, Java scores
**Reason:** BM25 `_score` depends on term statistics of the whole index and the query shape — not comparable across queries and not interpretable. A small Java scorer gives a stable 0–100 with one explained signal per rule, and is unit-testable without infrastructure.
**Trade-off:** Name normalization exists twice (ES analyzer + `NameNormalizer`); a unit test fails if the abbreviation lists diverge.

### D11. Type and country boost ranking, never filter
**Reason:** Filtering would silently hide a true match whose record is misclassified. Instead the scorer shows a visible penalty (−15 / −10).
**Trade-off:** A few more low-scoring candidates retrieved; the min-score threshold trims them.

### D12. Single-word abbreviation synonyms only (`corp => corporation`)
**Reason:** Multi-word synonyms need `synonym_graph`, which does not combine with fuzzy matching. Single-token replacements work with fuzziness and keep the analyzer simple.
**Trade-off:** No `"LLC" ↔ "limited liability company"` expansion.

### D13. Jaro-Winkler with a 0.80 per-word noise floor
**Reason:** Jaro-Winkler is a standard name-matching metric that rewards shared prefixes (typos later in a name cost less). Without the floor, unrelated short words earned partial credit ("doe" vs "john" = 0.53), pushing "John Doe" to 53 against "John Smith".
**Trade-off:** Hand-tuned threshold; documented and covered by `differentSurnameScoresLow`.

### D14. Reseed via new index + atomic alias swap
**Reason:** Spring Boot runs `ApplicationRunner`s *after* Tomcat starts accepting requests. Delete-and-recreate left a ~2 s window returning 503 or — worse — empty results that look like "no match". Swapping an alias is atomic, so searches see the old or new complete index.
**Trade-off:** ~15 extra lines; physical index names carry a timestamp.

### D15. Persist a snapshot of each candidate (V2)
**Reason:** A case must show what the analyst saw at decision time, even after the watchlist is reseeded or edited. This is audit evidence, not a second watchlist: it is never searched.
**Trade-off:** A few denormalized columns per candidate.

### D16. Optimistic locking (`@Version`) on candidates, not pessimistic locks
**Reason:** Conflicts are rare (two analysts on the same candidate at the same instant). Optimistic locking holds no row locks during the request and turns a would-be lost update into a clear 409.
**Trade-off:** The client must reload and retry on 409. Stale UI views (decided minutes after loading) are not detected — that would need the client to send the version it saw.

### D17. DTOs built inside the transaction; open-in-view disabled
**Reason:** Lazy associations are only loadable while the persistence context is open. Mapping to records in the service makes the SQL predictable (verified: 2 queries per case) and keeps JPA entities out of the API contract.
**Trade-off:** A mapping method (`CaseDetail.from`).

### D18. History via a single JPQL aggregate query
**Reason:** `count`/`max`/conditional `sum` over a LEFT JOIN returns everything the history list needs in one query, instead of loading every case with its candidates.
**Trade-off:** JPQL constructor expression references the DTO by fully-qualified name.
