package com.example.sentinelai.scoring;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Turns a raw name into comparable tokens: strip accents, lower-case, drop punctuation,
 * and map common abbreviations to one canonical word ("Corp." -> "corporation").
 *
 * <p>The abbreviation map mirrors the {@code name_abbreviations} synonym filter in
 * {@code elasticsearch/watchlist-index.json}; {@code NameNormalizerTest} fails if they drift apart.
 */
public final class NameNormalizer {

    /** abbreviation -> canonical form. Single words only (multi-word synonyms break fuzzy matching in ES). */
    public static final Map<String, String> ABBREVIATIONS = Map.ofEntries(
            Map.entry("corp", "corporation"),
            Map.entry("inc", "incorporated"),
            Map.entry("ltd", "limited"),
            Map.entry("co", "company"),
            Map.entry("intl", "international"),
            Map.entry("grp", "group"),
            Map.entry("hldg", "holding"),
            Map.entry("hldgs", "holdings"),
            Map.entry("mfg", "manufacturing"),
            Map.entry("svcs", "services"),
            Map.entry("bros", "brothers"),
            Map.entry("assoc", "association"),
            Map.entry("natl", "national"));

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\p{L}\\p{N}]+");

    private NameNormalizer() {
    }

    public static List<String> tokens(String name) {
        if (name == null) {
            return List.of();
        }
        // NFD splits "é" into "e" + combining accent; we then delete the accent marks.
        String folded = DIACRITICS.matcher(Normalizer.normalize(name, Normalizer.Form.NFD)).replaceAll("");
        return Arrays.stream(NON_ALPHANUMERIC.split(folded.toLowerCase()))
                .filter(token -> !token.isEmpty())
                .map(token -> ABBREVIATIONS.getOrDefault(token, token))
                .toList();
    }

    /** Tokens sorted alphabetically and joined: makes "Smith John" and "John Smith" identical. */
    public static String sortedKey(List<String> tokens) {
        return String.join(" ", tokens.stream().sorted().toList());
    }

    /** Identifiers compare on letters and digits only, upper-cased: "gb-0987 6543" == "GB09876543". */
    public static String normalizeIdentifier(String identifier) {
        return identifier == null ? "" : identifier.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    }
}
