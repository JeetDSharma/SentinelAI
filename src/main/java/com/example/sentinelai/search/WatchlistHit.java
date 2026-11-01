package com.example.sentinelai.search;

import com.example.sentinelai.domain.WatchlistRecord;

/**
 * A record Elasticsearch considered relevant, plus its BM25 {@code _score}.
 * The search score only ranks hits within one query; it is not a match-quality or risk measure.
 */
public record WatchlistHit(WatchlistRecord record, double searchScore) {
}
