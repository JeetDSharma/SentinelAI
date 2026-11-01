package com.example.sentinelai.scoring;

import com.example.sentinelai.domain.WatchlistRecord;

/** A retrieved watchlist record with both scores side by side: search relevance and match assessment. */
public record ScoredCandidate(WatchlistRecord record, double searchScore, MatchResult match) {
}
