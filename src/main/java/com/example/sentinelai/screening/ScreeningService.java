package com.example.sentinelai.screening;

import com.example.sentinelai.domain.ScreeningQuery;
import com.example.sentinelai.review.CaseDetail;
import com.example.sentinelai.review.CaseReviewService;
import com.example.sentinelai.scoring.MatchScoringService;
import com.example.sentinelai.scoring.ScoredCandidate;
import com.example.sentinelai.search.EntitySearchService;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Orchestrates a screening: retrieve (Elasticsearch) -> score (pure Java) -> persist (PostgreSQL).
 *
 * <p>Intentionally NOT {@code @Transactional}. The network call to Elasticsearch happens with no transaction
 * and no pooled DB connection held; only the final save opens a short transaction inside
 * {@link CaseReviewService}. If search fails, nothing is written.
 */
@Service
public class ScreeningService {

    private final EntitySearchService searchService;
    private final MatchScoringService scoringService;
    private final CaseReviewService caseReviewService;
    private final int minScore;

    public ScreeningService(EntitySearchService searchService,
                            MatchScoringService scoringService,
                            CaseReviewService caseReviewService,
                            @Value("${sentinel.screening.min-score}") int minScore) {
        this.searchService = searchService;
        this.scoringService = scoringService;
        this.caseReviewService = caseReviewService;
        this.minScore = minScore;
    }

    public CaseDetail screen(ScreeningQuery query) {
        return caseReviewService.createCase(query, scoreCandidates(query));
    }

    /** Search + score only; no persistence. */
    public List<ScoredCandidate> scoreCandidates(ScreeningQuery query) {
        return searchService.findCandidates(query).stream()
                .map(hit -> new ScoredCandidate(hit.record(), hit.searchScore(), scoringService.score(query, hit.record())))
                .filter(candidate -> candidate.match().score() >= minScore)
                .sorted(Comparator.comparingInt((ScoredCandidate c) -> c.match().score()).reversed()
                        .thenComparing(Comparator.comparingDouble(ScoredCandidate::searchScore).reversed()))
                .toList();
    }
}
