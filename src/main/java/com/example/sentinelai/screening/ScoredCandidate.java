package com.example.sentinelai.screening;

import com.example.sentinelai.domain.WatchlistRecord;
import com.example.sentinelai.scoring.MatchResult;

/** A retrieved watchlist record with both scores side by side: search relevance and match assessment. */
public record ScoredCandidate(WatchlistRecord record, double searchScore, MatchResult match) {
}
