package com.example.sentinelai.screening;

import com.example.sentinelai.domain.EntityType;
import com.example.sentinelai.domain.ScreeningQuery;
import com.example.sentinelai.scoring.MatchResult;
import com.example.sentinelai.scoring.MatchSignal;
import java.util.List;

/** Response of {@code POST /api/screenings}. */
public record ScreeningResponse(ScreeningQuery query, List<Candidate> candidates) {

    public record Candidate(
            String watchlistId,
            String name,
            List<String> aliases,
            EntityType entityType,
            String country,
            String program,
            String remarks,
            int matchScore,
            MatchResult.Band band,
            String matchedName,
            List<MatchSignal> signals,
            /* Elasticsearch BM25 relevance: ranks hits within this query only. Not a risk score. */
            double searchRelevance) {
    }

    static ScreeningResponse of(ScreeningQuery query, List<ScoredCandidate> scored) {
        return new ScreeningResponse(query, scored.stream().map(ScreeningResponse::toCandidate).toList());
    }

    private static Candidate toCandidate(ScoredCandidate c) {
        var r = c.record();
        var m = c.match();
        return new Candidate(r.id(), r.name(), r.aliases(), r.entityType(), r.country(), r.program(), r.remarks(),
                m.score(), m.band(), m.matchedName(), m.signals(), c.searchScore());
    }
}
