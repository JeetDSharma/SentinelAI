-- Snapshot of the watchlist record as the analyst saw it at screening time, so case history stays
-- accurate even if the synthetic watchlist is later changed or reseeded. (Evidence, not a second watchlist.)
ALTER TABLE case_candidate
    ADD COLUMN record_name        VARCHAR(200)     NOT NULL,
    ADD COLUMN record_entity_type VARCHAR(20)      NOT NULL CHECK (record_entity_type IN ('PERSON', 'ORGANIZATION')),
    ADD COLUMN record_country     VARCHAR(2),
    ADD COLUMN program            VARCHAR(100),
    ADD COLUMN remarks            VARCHAR(1000),
    ADD COLUMN search_relevance   DOUBLE PRECISION NOT NULL;
