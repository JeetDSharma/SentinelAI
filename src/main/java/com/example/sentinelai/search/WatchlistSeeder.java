package com.example.sentinelai.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import com.example.sentinelai.domain.WatchlistRecord;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Rebuilds the watchlist index from classpath JSON at startup. Idempotent: every run produces an index with
 * the same fixed document ids and content, and the previous index is removed. Disable with
 * {@code sentinel.watchlist.seed-on-startup=false}.
 *
 * <p>Note: an {@link ApplicationRunner} runs after the web server has started accepting requests,
 * which is why seeding must never leave the searchable index missing or partially filled.
 *
 * <p>If Elasticsearch is down the app still starts (screening returns 503 until it is back and the app restarts).
 */
@Component
@ConditionalOnProperty(name = "sentinel.watchlist.seed-on-startup", havingValue = "true", matchIfMissing = true)
public class WatchlistSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(WatchlistSeeder.class);
    private static final String INDEX_DEFINITION = "elasticsearch/watchlist-index.json";
    private static final String RECORDS = "elasticsearch/watchlist.json";

    private final ElasticsearchClient client;
    private final JsonMapper jsonMapper;
    private final String index;

    public WatchlistSeeder(ElasticsearchClient client, JsonMapper jsonMapper,
                           @Value("${sentinel.watchlist.index}") String index) {
        this.client = client;
        this.jsonMapper = jsonMapper;
        this.index = index;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            int count = seed();
            log.info("Seeded Elasticsearch index '{}' with {} synthetic watchlist records", index, count);
        } catch (IOException | RuntimeException e) {
            log.error("Watchlist seeding failed; screening will be unavailable until Elasticsearch is reachable", e);
        }
    }

    /**
     * Builds a fresh physical index ({@code watchlist-<timestamp>}), fills it, then atomically repoints the
     * {@code watchlist} alias to it and deletes the previous one. Searches always go through the alias, so
     * they see either the old complete index or the new complete one — never a missing or half-filled index.
     */
    public int seed() throws IOException {
        List<WatchlistRecord> records = loadRecords();
        String physicalIndex = index + "-" + System.currentTimeMillis();

        try (InputStream definition = new ClassPathResource(INDEX_DEFINITION).getInputStream()) {
            client.indices().create(c -> c.index(physicalIndex).withJson(definition));
        }

        List<BulkOperation> operations = records.stream()
                .map(r -> BulkOperation.of(op -> op.index(i -> i.index(physicalIndex).id(r.id()).document(r))))
                .toList();
        // refresh=wait_for: the call returns only once the documents are visible to search.
        BulkResponse response = client.bulk(b -> b.operations(operations).refresh(Refresh.WaitFor));
        if (response.errors()) {
            throw new IllegalStateException("Bulk indexing reported errors: " + response.items().stream()
                    .filter(item -> item.error() != null)
                    .map(item -> item.id() + ": " + item.error().reason())
                    .toList());
        }

        swapAlias(physicalIndex);
        return records.size();
    }

    private void swapAlias(String newIndex) throws IOException {
        List<String> previous = List.of();
        if (client.indices().existsAlias(e -> e.name(index)).value()) {
            previous = List.copyOf(client.indices().getAlias(g -> g.name(index)).aliases().keySet());
        } else if (client.indices().exists(e -> e.index(index)).value()) {
            // A concrete index already uses the alias name (e.g. from an older seeding approach): remove it.
            client.indices().delete(d -> d.index(index));
        }
        List<String> toRemove = previous;
        client.indices().updateAliases(u -> {
            u.actions(a -> a.add(add -> add.index(newIndex).alias(index)));
            if (!toRemove.isEmpty()) {
                u.actions(a -> a.removeIndex(r -> r.indices(toRemove))); // same atomic request as the add
            }
            return u;
        });
    }

    private List<WatchlistRecord> loadRecords() throws IOException {
        try (InputStream in = new ClassPathResource(RECORDS).getInputStream()) {
            return Arrays.asList(jsonMapper.readValue(in, WatchlistRecord[].class));
        }
    }
}
