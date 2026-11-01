package com.example.sentinelai.domain;

/**
 * What the analyst asked us to screen, already cleaned up (trimmed, blank -> null, country upper-cased).
 * Separate from the web DTO so search and scoring never depend on HTTP/validation types.
 */
public record ScreeningQuery(String name, EntityType entityType, String country, String identifier) {

    public static ScreeningQuery of(String name, EntityType entityType, String country, String identifier) {
        String cleanCountry = blankToNull(country);
        return new ScreeningQuery(
                name.trim(),
                entityType,
                cleanCountry == null ? null : cleanCountry.toUpperCase(),
                blankToNull(identifier));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
