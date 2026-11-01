-- Screening cases and analyst decisions. Watchlist records live only in Elasticsearch;
-- case_candidate stores a snapshot of what was shown to the analyst (audit evidence), not a copy of the watchlist.

CREATE TABLE screening_case (
    id           BIGSERIAL    PRIMARY KEY,
    query_name   VARCHAR(200) NOT NULL,
    entity_type  VARCHAR(20)  NOT NULL CHECK (entity_type IN ('PERSON', 'ORGANIZATION')),
    country      VARCHAR(2),
    identifier   VARCHAR(100),
    status       VARCHAR(20)  NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'COMPLETED')),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_screening_case_created_at ON screening_case (created_at DESC);

CREATE TABLE case_candidate (
    id            BIGSERIAL    PRIMARY KEY,
    case_id       BIGINT       NOT NULL REFERENCES screening_case (id) ON DELETE CASCADE,
    watchlist_id  VARCHAR(64)  NOT NULL,
    matched_name  VARCHAR(200) NOT NULL,
    score         INT          NOT NULL CHECK (score BETWEEN 0 AND 100),
    signals       JSONB        NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                  CHECK (status IN ('PENDING', 'REVIEWED', 'DISMISSED', 'ESCALATED')),
    version       BIGINT       NOT NULL DEFAULT 0,   -- JPA optimistic locking
    UNIQUE (case_id, watchlist_id)
);

CREATE INDEX idx_case_candidate_case_id ON case_candidate (case_id);

-- Append-only audit trail: every analyst decision, including changes of mind.
CREATE TABLE review_decision (
    id            BIGSERIAL    PRIMARY KEY,
    candidate_id  BIGINT       NOT NULL REFERENCES case_candidate (id) ON DELETE CASCADE,
    decision      VARCHAR(20)  NOT NULL CHECK (decision IN ('REVIEWED', 'DISMISSED', 'ESCALATED')),
    note          VARCHAR(2000),
    decided_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_review_decision_candidate_id ON review_decision (candidate_id);
