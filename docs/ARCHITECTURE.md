# Architecture

```
React (Vite, Mantine) ──/api──▶ Spring Boot modular monolith
                                 ├─ ScreeningController        REST, validation, ProblemDetail errors
                                 ├─ ScreeningService           orchestration — no transaction
                                 │   ├─ EntitySearchService ──▶ Elasticsearch  (watchlist index, candidate retrieval)
                                 │   ├─ MatchScoringService     pure Java, deterministic score + signals
                                 │   └─ CaseReviewService ────▶ PostgreSQL     (@Transactional, short)
                                 └─ WatchlistSeeder            idempotent index (re)creation at startup
```

## Screening request flow
1. `POST /api/screenings` → validated `ScreeningRequest`.
2. `EntitySearchService` runs one bool query (exact + fuzzy + phrase + identifier clauses) → top 10 hits. **No DB transaction is open.**
3. `MatchScoringService` scores each hit 0–100 with explicit `MatchSignal`s; candidates under the threshold are dropped.
4. `CaseReviewService.createCase(...)` persists the case and candidate snapshots in one short transaction.
5. Response: case id + candidates (match score, band, signals, and ES `_score` labelled as search rank only).

## Review flow
`POST /api/screenings/{caseId}/candidates/{candidateId}/decisions` → one transaction: insert `review_decision`, update `case_candidate.status` (optimistic lock on `version`), mark case `COMPLETED` when no candidate is `PENDING`.

## Data ownership
| Store | Holds | Why |
|---|---|---|
| Elasticsearch | Watchlist records | Fuzzy/full-text retrieval; seeded from a JSON file, no sync needed |
| PostgreSQL | Cases, candidate snapshots, decisions | Transactions, constraints, audit trail |

Candidate snapshots (name, score, signals at screening time) are audit evidence — what the analyst actually saw — not a duplicated watchlist.

## Schema (Flyway `V1__init.sql`)
- `screening_case` (query fields, status, created_at)
- `case_candidate` (case FK, watchlist_id, matched_name, score, signals JSONB, status, version)
- `review_decision` (candidate FK, decision, note, decided_at) — append-only

## REST API
| Method | Path | Purpose |
|---|---|---|
| POST | `/api/screenings` | Screen an entity → case + candidates |
| GET | `/api/screenings?limit=20` | Recent history |
| GET | `/api/screenings/{id}` | Case detail |
| POST | `/api/screenings/{caseId}/candidates/{candidateId}/decisions` | Record a decision |
| GET | `/actuator/health` | DB + ES health |

## Candidate retrieval (Elasticsearch)
Index `watchlist` is an **alias** to a physical index `watchlist-<timestamp>` (see seeding below).

Mapping (`src/main/resources/elasticsearch/watchlist-index.json`, `dynamic: strict`):
- `name`, `aliases` — `text` with `name_analyzer` (standard tokenizer → lowercase → asciifolding → single-word abbreviation synonyms), plus `.exact` keyword subfield with a lowercase/asciifolding normalizer
- `identifiers` — `keyword` with a normalizer that strips non-alphanumerics and upper-cases (`gb-0987 6543` == `GB09876543`)
- `entityType`, `country`, `program` — `keyword`; `remarks` — stored, not indexed

Query (`EntitySearchService.buildQuery`), top 10:
```
bool
  must:   bool, minimum_should_match 1         -- must match on name OR identifier
            term name.exact / aliases.exact   boost 10
            multi_match phrase, slop 2        boost 3
            multi_match fuzziness AUTO, prefix_length 1, minimum_should_match "2<75%"
            term identifiers                  boost 20   (only if provided)
  should: term entityType, term country       -- ranking nudge, never a filter
```
Type and country never filter: a misclassified record must still reach the analyst; the scorer penalizes the conflict visibly.

## Match scoring (`MatchScoringService`)
| Signal | Points |
|---|---|
| Name similarity (best of primary name and aliases) | 0 … +70 (exact after normalization = 70) |
| Identifier | +20 match · −25 both present and different · 0 if either side missing |
| Country | +5 agree · −10 conflict · 0 if either side missing |
| Entity type | +5 agree · −15 conflict |

Score = sum of signal points, clamped to 0–100. Bands: HIGH ≥ 80, MEDIUM 60–79, LOW < 60. Candidates below `sentinel.screening.min-score` (50) are not shown.

Name similarity = `max(whole, token)` over normalized tokens (accents folded, punctuation dropped, abbreviations canonicalized):
- **whole**: Jaro-Winkler on alphabetically sorted tokens (handles word order and spacing differences)
- **token**: average best per-word Jaro-Winkler for query words (words below 0.80 count as 0), × (0.8 + 0.2 × fraction of record words matched) — partial names score below full ones

**Limitations (by design for a demo):** no phonetic matching (Soundex/Metaphone), no nickname tables (Bill ↔ William), no date-of-birth, weights are hand-picked not calibrated, Latin-script only. Reversed names like "Li Wei"/"Wei Li" are indistinguishable without an identifier — shown as two equal candidates.

## Seeding (`WatchlistSeeder`)
At startup (`sentinel.watchlist.seed-on-startup=true`): create `watchlist-<ts>` → bulk index the 60 records from `watchlist.json` with fixed ids (`refresh=wait_for`) → one atomic `_aliases` call adds the alias to the new index and removes the old index. Searches never observe a missing or partially filled index. If Elasticsearch is down, seeding logs an error and the app still starts; screening returns 503.
