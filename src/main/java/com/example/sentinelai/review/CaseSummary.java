package com.example.sentinelai.review;

import com.example.sentinelai.domain.EntityType;
import java.time.Instant;

/** Row of the screening-history list, built directly by a JPQL constructor expression. */
public record CaseSummary(
        Long id,
        String queryName,
        EntityType entityType,
        String country,
        ScreeningCase.Status status,
        Instant createdAt,
        Long candidateCount,
        Integer topScore,
        Long pendingCount,
        Long escalatedCount) {

    public CaseSummary {
        // SUM over zero rows (a case with no candidates) is NULL in SQL.
        pendingCount = pendingCount == null ? 0L : pendingCount;
        escalatedCount = escalatedCount == null ? 0L : escalatedCount;
    }
}
