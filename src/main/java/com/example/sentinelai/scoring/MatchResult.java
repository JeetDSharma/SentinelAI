package com.example.sentinelai.scoring;

import java.util.List;

/**
 * Deterministic match assessment for one candidate.
 *
 * @param score       0–100, sum of signal points clamped to that range
 * @param band        coarse bucket for the UI
 * @param matchedName the record name or alias that best matched the query
 * @param signals     evidence, one entry per rule that fired
 */
public record MatchResult(int score, Band band, String matchedName, List<MatchSignal> signals) {

    public enum Band {
        HIGH, MEDIUM, LOW;

        public static Band of(int score) {
            if (score >= 80) {
                return HIGH;
            }
            return score >= 60 ? MEDIUM : LOW;
        }
    }
}
