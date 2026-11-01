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

_Matching and scoring details are added in Phase 2._
