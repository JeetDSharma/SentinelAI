package com.example.sentinelai.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.example.sentinelai.domain.ScreeningQuery;
import com.example.sentinelai.domain.WatchlistRecord;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Candidate retrieval: one Elasticsearch query that is deliberately generous (high recall).
 * Precision and explanation are the job of {@code MatchScoringService}.
 */
@Service
public class EntitySearchService {

    static final int MAX_CANDIDATES = 10;

    private final ElasticsearchClient client;
    private final String index;

    public EntitySearchService(ElasticsearchClient client, @Value("${sentinel.watchlist.index}") String index) {
        this.client = client;
        this.index = index;
    }

    public List<WatchlistHit> findCandidates(ScreeningQuery query) {
        SearchResponse<WatchlistRecord> response;
        try {
            response = client.search(s -> s
                    .index(index)
                    .size(MAX_CANDIDATES)
                    .query(buildQuery(query)), WatchlistRecord.class);
        } catch (IOException | ElasticsearchException e) {
            // IOException covers connection refused / timeouts (TransportException extends it);
            // ElasticsearchException covers error responses such as index_not_found.
            throw new SearchUnavailableException("Watchlist search failed: " + e.getMessage(), e);
        }
        return response.hits().hits().stream()
                .filter(hit -> hit.source() != null)
                .map(hit -> new WatchlistHit(hit.source(), Objects.requireNonNullElse(hit.score(), 0.0)))
                .toList();
    }

    /**
     * <pre>
     * bool
     *   must:   bool (at least one should)            -- a record must match on name or identifier
     *             exact name / alias      boost 10    -- keyword subfield, normalized (case, accents)
     *             phrase name / alias     boost 3     -- words in order, small gaps allowed
     *             fuzzy name / alias                  -- typos (AUTO edits), any word order, partial names
     *             identifier              boost 20    -- normalized keyword ("GB-0987" == "gb0987")
     *   should: entityType, country                   -- nudge ranking only; never exclude a record
     * </pre>
     */
    static Query buildQuery(ScreeningQuery query) {
        String name = query.name();
        Query nameOrIdentifier = Query.of(q -> q.bool(b -> {
            b.should(s -> s.term(t -> t.field("name.exact").value(name).boost(10f)));
            b.should(s -> s.term(t -> t.field("aliases.exact").value(name).boost(10f)));
            b.should(s -> s.multiMatch(m -> m
                    .query(name).fields("name", "aliases")
                    .type(TextQueryType.Phrase).slop(2).boost(3f)));
            b.should(s -> s.multiMatch(m -> m
                    .query(name).fields("name", "aliases")
                    .fuzziness("AUTO")             // 0 edits for 1-2 chars, 1 for 3-5, 2 for 6+
                    .prefixLength(1)               // first letter must match: far fewer term expansions
                    .minimumShouldMatch("2<75%"))); // 1-2 words: all required; more: 75% of them
            if (query.identifier() != null) {
                b.should(s -> s.term(t -> t.field("identifiers").value(query.identifier()).boost(20f)));
            }
            return b.minimumShouldMatch("1");
        }));

        return Query.of(q -> q.bool(b -> {
            b.must(nameOrIdentifier);
            b.should(s -> s.term(t -> t.field("entityType").value(query.entityType().name())));
            if (query.country() != null) {
                b.should(s -> s.term(t -> t.field("country").value(query.country())));
            }
            return b;
        }));
    }
}
