package com.example.sentinelai.scoring;

/** One piece of evidence behind a match score. The score is exactly the (clamped) sum of signal points. */
public record MatchSignal(Type type, int points, String detail) {

    public enum Type {
        NAME_EXACT,
        NAME_SIMILAR,
        IDENTIFIER_MATCH,
        IDENTIFIER_MISMATCH,
        COUNTRY_MATCH,
        COUNTRY_MISMATCH,
        ENTITY_TYPE_MATCH,
        ENTITY_TYPE_MISMATCH
    }
}
