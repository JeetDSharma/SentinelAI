package com.example.sentinelai.review;

import com.example.sentinelai.domain.EntityType;
import com.example.sentinelai.scoring.MatchResult;
import com.example.sentinelai.scoring.MatchSignal;
import java.time.Instant;
import java.util.List;

/**
 * API view of a case. Built inside the service transaction (open-in-view is off), so controllers never touch
 * lazy entity associations and JSON serialization never triggers SQL.
 */
public record CaseDetail(
        Long id,
        String queryName,
        EntityType entityType,
        String country,
        String identifier,
        ScreeningCase.Status status,
        Instant createdAt,
        List<Candidate> candidates) {

    public record Candidate(
            Long id,
            String watchlistId,
            String name,
            EntityType entityType,
            String country,
            String program,
            String remarks,
            String matchedName,
            int matchScore,
            MatchResult.Band band,
            List<MatchSignal> signals,
            /* Elasticsearch BM25 relevance: ranks hits within one query only. Not a risk score. */
            double searchRelevance,
            CaseCandidate.Status status,
            List<Decision> decisions) {
    }

    public record Decision(Long id, ReviewDecision.Decision decision, String note, Instant decidedAt) {
    }

    static CaseDetail from(ScreeningCase c) {
        return new CaseDetail(c.getId(), c.getQueryName(), c.getEntityType(), c.getCountry(), c.getIdentifier(),
                c.getStatus(), c.getCreatedAt(), c.getCandidates().stream().map(CaseDetail::candidate).toList());
    }

    private static Candidate candidate(CaseCandidate k) {
        return new Candidate(k.getId(), k.getWatchlistId(), k.getRecordName(), k.getRecordEntityType(),
                k.getRecordCountry(), k.getProgram(), k.getRemarks(), k.getMatchedName(), k.getScore(),
                MatchResult.Band.of(k.getScore()), k.getSignals(), k.getSearchRelevance(), k.getStatus(),
                k.getDecisions().stream()
                        .map(d -> new Decision(d.getId(), d.getDecision(), d.getNote(), d.getDecidedAt()))
                        .toList());
    }
}
