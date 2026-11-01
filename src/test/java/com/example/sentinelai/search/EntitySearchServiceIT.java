package com.example.sentinelai.search;

import static com.example.sentinelai.domain.EntityType.ORGANIZATION;
import static com.example.sentinelai.domain.EntityType.PERSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.example.sentinelai.TestcontainersConfiguration;
import com.example.sentinelai.domain.EntityType;
import com.example.sentinelai.domain.ScreeningQuery;
import com.example.sentinelai.screening.ScoredCandidate;
import com.example.sentinelai.screening.ScreeningService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Real Elasticsearch (Testcontainers) seeded from the real watchlist.json at startup.
 * These cases double as the documented demo examples.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class EntitySearchServiceIT {

    @Autowired EntitySearchService searchService;
    @Autowired ScreeningService screeningService;
    @Autowired WatchlistSeeder seeder;
    @Autowired ElasticsearchClient client;

    @ParameterizedTest(name = "[{0}] {1} -> {3}")
    @CsvSource({
            "exact alias,       Viktor Petrov,                    PERSON,       WL-0001",
            "token order,       Petrov Viktor,                    PERSON,       WL-0001",
            "misspelling,       Viktor Petrof,                    PERSON,       WL-0001",
            "accents dropped,   Jose Antonio Garcia Ruiz,         PERSON,       WL-0009",
            "partial name,      Jose Garcia,                      PERSON,       WL-0009",
            "transliteration,   Mohamed Al Rashid,                PERSON,       WL-0003",
            "transliteration,   Alexander Volkov,                 PERSON,       WL-0011",
            "diacritic letter,  Soren Halvorsen,                  PERSON,       WL-0010",
            "spelling variant,  Dmitry Kuznetsov,                 PERSON,       WL-0017",
            "abbreviation,      Orion Global Trading Corp,        ORGANIZATION, WL-0103",
            "abbreviations,     Northwind Maritime Holdings Ltd,  ORGANIZATION, WL-0101",
            "abbreviation,      Blue Lotus Intl Shipping,         ORGANIZATION, WL-0105",
            "typo in org,       Meridan Precision Manufacturing,  ORGANIZATION, WL-0109",
    })
    void findsExpectedRecordAsTopScoredCandidate(String scenario, String name, EntityType type, String expectedId) {
        ScreeningQuery query = ScreeningQuery.of(name, type, null, null);

        List<String> searchIds = searchService.findCandidates(query).stream().map(h -> h.record().id()).toList();
        assertThat(searchIds).as("Elasticsearch top 3 for '%s'", name).contains(expectedId);
        assertThat(searchIds.indexOf(expectedId)).isLessThan(3);

        List<ScoredCandidate> scored = screeningService.screen(query);
        assertThat(scored).as("scored candidates for '%s'", name).isNotEmpty();
        assertThat(scored.getFirst().record().id()).isEqualTo(expectedId);
    }

    @Test
    void identifierAloneRetrievesRecordWhenNameIsUnrelated() {
        var hits = searchService.findCandidates(
                ScreeningQuery.of("Completely Different Name", ORGANIZATION, null, "reg-gb-0987 6543"));
        assertThat(hits).extracting(h -> h.record().id()).first().isEqualTo("WL-0101");
    }

    @Test
    void reversedNamesAreBothReturnedAndIdentifierDisambiguates() {
        List<ScoredCandidate> nameOnly = screeningService.screen(ScreeningQuery.of("Li Wei", PERSON, null, null));
        assertThat(nameOnly).extracting(c -> c.record().id()).contains("WL-0007", "WL-0008");

        List<ScoredCandidate> withId = screeningService.screen(ScreeningQuery.of("Li Wei", PERSON, null, "P-SG-K7712098"));
        assertThat(withId.getFirst().record().id()).isEqualTo("WL-0008");
    }

    @Test
    void entityTypeMismatchIsRankedBelowMatchingType() {
        List<ScoredCandidate> scored = screeningService.screen(ScreeningQuery.of("Petrova Consulting", PERSON, null, null));
        // The organization is still shown (type is a penalty, not a filter) ...
        assertThat(scored).extracting(c -> c.record().id()).contains("WL-0002");
        // ... but with the type-mismatch penalty visible in its evidence.
        assertThat(scored.stream().filter(c -> c.record().id().equals("WL-0002")).findFirst().orElseThrow()
                .match().signals()).anyMatch(s -> s.type().name().equals("ENTITY_TYPE_MISMATCH"));
    }

    @Test
    void unrelatedNameProducesNoScoredCandidates() {
        assertThat(screeningService.screen(ScreeningQuery.of("Zebulon Quackenbush", PERSON, null, null))).isEmpty();
    }

    @Test
    void seedingIsIdempotent() throws Exception {
        int first = seeder.seed();
        int second = seeder.seed();
        long indexed = client.count(c -> c.index("watchlist")).count();

        assertThat(first).isEqualTo(second);
        assertThat(indexed).isEqualTo(second);
        // The alias points at exactly one physical index; the previous one was removed in the same swap.
        assertThat(client.indices().getAlias(g -> g.name("watchlist")).aliases()).hasSize(1);
        assertThat(client.indices().get(g -> g.index("watchlist-*")).indices()).hasSize(1);
    }

    @Test
    void missingIndexIsReportedAsSearchUnavailable() {
        var service = new EntitySearchService(client, "no-such-index");
        assertThatThrownBy(() -> service.findCandidates(ScreeningQuery.of("Viktor Petrov", PERSON, null, null)))
                .isInstanceOf(SearchUnavailableException.class);
    }

    @Test
    void unreachableClusterIsReportedAsSearchUnavailable() {
        var unreachable = ElasticsearchClient.of(b -> b.host("http://localhost:1"));
        var service = new EntitySearchService(unreachable, "watchlist");
        assertThatThrownBy(() -> service.findCandidates(ScreeningQuery.of("Viktor Petrov", PERSON, null, null)))
                .isInstanceOf(SearchUnavailableException.class);
    }
}
