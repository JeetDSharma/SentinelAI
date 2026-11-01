package com.example.sentinelai.scoring;

import com.example.sentinelai.domain.ScreeningQuery;
import com.example.sentinelai.domain.WatchlistRecord;
import com.example.sentinelai.scoring.MatchSignal.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.springframework.stereotype.Service;

/**
 * Deterministic, explainable match score (0–100) for one query/record pair.
 *
 * <pre>
 *   Name similarity      0 .. +70   best of primary name and aliases
 *   Identifier           +20 match / -25 both present but different
 *   Country              +5 agree  / -10 both present but different
 *   Entity type          +5 agree  / -15 different
 * </pre>
 *
 * Independent of Elasticsearch's _score: ES decides which records are worth looking at,
 * this class decides how strong each match is and why. No I/O, no state: safe to call from any thread.
 */
@Service
public class MatchScoringService {

    static final int NAME_MAX = 70;
    static final int IDENTIFIER_MATCH = 20;
    static final int IDENTIFIER_MISMATCH = -25;
    static final int COUNTRY_MATCH = 5;
    static final int COUNTRY_MISMATCH = -10;
    static final int TYPE_MATCH = 5;
    static final int TYPE_MISMATCH = -15;

    /** Two tokens at or above this Jaro-Winkler similarity count as "the same word" (allows small typos). */
    private static final double TOKEN_MATCH_THRESHOLD = 0.9;

    /**
     * Below this, two words are treated as unrelated (0). Jaro-Winkler otherwise gives real credit to
     * unrelated short words ("doe" vs "john" = 0.53), which inflated scores for different surnames.
     */
    private static final double TOKEN_NOISE_FLOOR = 0.8;

    private final JaroWinklerSimilarity jaroWinkler = new JaroWinklerSimilarity();

    public MatchResult score(ScreeningQuery query, WatchlistRecord record) {
        List<MatchSignal> signals = new ArrayList<>();

        NameMatch name = bestNameMatch(query.name(), record);
        signals.add(nameSignal(name));
        identifierSignal(query, record, signals);
        countrySignal(query, record, signals);
        signals.add(query.entityType() == record.entityType()
                ? new MatchSignal(Type.ENTITY_TYPE_MATCH, TYPE_MATCH, "Both are " + label(record.entityType()))
                : new MatchSignal(Type.ENTITY_TYPE_MISMATCH, TYPE_MISMATCH,
                        "Searched for " + label(query.entityType()) + ", record is " + label(record.entityType())));

        int total = signals.stream().mapToInt(MatchSignal::points).sum();
        int score = Math.clamp(total, 0, 100);
        return new MatchResult(score, MatchResult.Band.of(score), name.name(), List.copyOf(signals));
    }

    // ---- name -------------------------------------------------------------------------------

    private record NameMatch(String name, boolean alias, double similarity, boolean exact) {
    }

    private NameMatch bestNameMatch(String queryName, WatchlistRecord record) {
        List<String> queryTokens = NameNormalizer.tokens(queryName);
        NameMatch best = compare(queryTokens, record.name(), false);
        for (String alias : record.aliases()) {
            NameMatch candidate = compare(queryTokens, alias, true);
            if (candidate.similarity() > best.similarity()) { // strict: on ties prefer the primary name
                best = candidate;
            }
        }
        return best;
    }

    private NameMatch compare(List<String> queryTokens, String recordName, boolean alias) {
        List<String> recordTokens = NameNormalizer.tokens(recordName);
        if (queryTokens.isEmpty() || recordTokens.isEmpty()) {
            return new NameMatch(recordName, alias, 0.0, false);
        }
        String queryKey = NameNormalizer.sortedKey(queryTokens);
        String recordKey = NameNormalizer.sortedKey(recordTokens);
        if (queryKey.equals(recordKey)) {
            return new NameMatch(recordName, alias, 1.0, true);
        }
        return new NameMatch(recordName, alias, similarity(queryTokens, recordTokens, queryKey, recordKey), false);
    }

    /**
     * Max of two views, so each covers the other's blind spot:
     * <ul>
     *   <li><b>whole</b>: Jaro-Winkler on token-sorted strings — robust to spacing ("AlRashid" vs "Al Rashid").</li>
     *   <li><b>token</b>: how well each query word is found in the record (typos allowed), discounted when the
     *       record has extra words the query didn't mention (partial names score below full ones).</li>
     * </ul>
     */
    double similarity(List<String> queryTokens, List<String> recordTokens, String queryKey, String recordKey) {
        double whole = jaroWinkler.apply(queryKey, recordKey);

        double queryCoverage = queryTokens.stream()
                .mapToDouble(q -> bestTokenSimilarity(q, recordTokens))
                .average().orElse(0);
        double recordCoverage = recordTokens.stream()
                .filter(r -> bestTokenSimilarity(r, queryTokens) >= TOKEN_MATCH_THRESHOLD)
                .count() / (double) recordTokens.size();
        double token = queryCoverage * (0.8 + 0.2 * recordCoverage);

        return Math.max(whole, token);
    }

    private double bestTokenSimilarity(String token, List<String> others) {
        double best = others.stream().mapToDouble(o -> jaroWinkler.apply(token, o)).max().orElse(0);
        return best >= TOKEN_NOISE_FLOOR ? best : 0.0;
    }

    private static MatchSignal nameSignal(NameMatch match) {
        String which = (match.alias() ? "alias" : "primary name") + " '" + match.name() + "'";
        if (match.exact()) {
            return new MatchSignal(Type.NAME_EXACT, NAME_MAX,
                    "Exact match to " + which + " (ignoring case, accents, word order, abbreviations)");
        }
        return new MatchSignal(Type.NAME_SIMILAR, (int) Math.round(NAME_MAX * match.similarity()),
                String.format(Locale.ROOT, "Name similarity %.2f to %s", match.similarity(), which));
    }

    // ---- attributes ---------------------------------------------------------------------------

    private static void identifierSignal(ScreeningQuery query, WatchlistRecord record, List<MatchSignal> signals) {
        if (query.identifier() == null || record.identifiers().isEmpty()) {
            return; // unknown on one side: no evidence either way
        }
        String wanted = NameNormalizer.normalizeIdentifier(query.identifier());
        record.identifiers().stream()
                .filter(id -> NameNormalizer.normalizeIdentifier(id).equals(wanted))
                .findFirst()
                .ifPresentOrElse(
                        id -> signals.add(new MatchSignal(Type.IDENTIFIER_MATCH, IDENTIFIER_MATCH,
                                "Identifier matches record identifier " + id)),
                        () -> signals.add(new MatchSignal(Type.IDENTIFIER_MISMATCH, IDENTIFIER_MISMATCH,
                                "Identifier " + query.identifier() + " not among record identifiers "
                                        + record.identifiers())));
    }

    private static void countrySignal(ScreeningQuery query, WatchlistRecord record, List<MatchSignal> signals) {
        if (query.country() == null || record.country() == null) {
            return;
        }
        signals.add(query.country().equalsIgnoreCase(record.country())
                ? new MatchSignal(Type.COUNTRY_MATCH, COUNTRY_MATCH, "Country agrees (" + record.country() + ")")
                : new MatchSignal(Type.COUNTRY_MISMATCH, COUNTRY_MISMATCH,
                        "Country differs: searched " + query.country() + ", record " + record.country()));
    }

    private static String label(Enum<?> type) {
        return type.name().toLowerCase(Locale.ROOT);
    }
}
