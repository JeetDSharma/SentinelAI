package com.example.sentinelai.domain;

import java.util.List;

/** One synthetic watchlist entry, exactly as stored in the Elasticsearch {@code watchlist} index. */
public record WatchlistRecord(
        String id,
        String name,
        List<String> aliases,
        EntityType entityType,
        String country,
        List<String> identifiers,
        String program,
        String remarks) {

    public WatchlistRecord {
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
        identifiers = identifiers == null ? List.of() : List.copyOf(identifiers);
    }
}
