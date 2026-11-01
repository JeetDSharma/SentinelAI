package com.example.sentinelai.screening;

import com.example.sentinelai.domain.ScreeningQuery;
import com.example.sentinelai.scoring.MatchScoringService;
import com.example.sentinelai.search.EntitySearchService;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Orchestrates a screening: retrieve (Elasticsearch) -> score (pure Java) -> filter and rank.
 * Intentionally NOT {@code @Transactional}: no database connection is held while Elasticsearch is called.
 */
@Service
public class ScreeningService {

    private final EntitySearchService searchService;
    private final MatchScoringService scoringService;
    private final int minScore;

    public ScreeningService(EntitySearchService searchService,
                            MatchScoringService scoringService,
                            @Value("${sentinel.screening.min-score}") int minScore) {
        this.searchService = searchService;
        this.scoringService = scoringService;
        this.minScore = minScore;
    }

    public List<ScoredCandidate> screen(ScreeningQuery query) {
        return searchService.findCandidates(query).stream()
                .map(hit -> new ScoredCandidate(hit.record(), hit.searchScore(), scoringService.score(query, hit.record())))
                .filter(candidate -> candidate.match().score() >= minScore)
                .sorted(Comparator.comparingInt((ScoredCandidate c) -> c.match().score()).reversed()
                        .thenComparing(Comparator.comparingDouble(ScoredCandidate::searchScore).reversed()))
                .toList();
    }
}
