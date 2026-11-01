package com.example.sentinelai.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class NameNormalizerTest {

    @Test
    void foldsAccentsCasePunctuationAndAbbreviations() {
        assertThat(NameNormalizer.tokens("José-Antonio GARCÍA, Ruiz")).containsExactly("jose", "antonio", "garcia", "ruiz");
        assertThat(NameNormalizer.tokens("Northwind Hldgs. Ltd")).containsExactly("northwind", "holdings", "limited");
        assertThat(NameNormalizer.tokens("Søren")).containsExactly("søren"); // ø is a letter, not letter+accent
    }

    @Test
    void sortedKeyIgnoresWordOrder() {
        assertThat(NameNormalizer.sortedKey(NameNormalizer.tokens("Smith John")))
                .isEqualTo(NameNormalizer.sortedKey(NameNormalizer.tokens("john SMITH")));
    }

    @Test
    void identifiersCompareOnAlphanumericsOnly() {
        assertThat(NameNormalizer.normalizeIdentifier("reg-gb 0987.6543")).isEqualTo("REGGB09876543");
    }

    /** Java scoring and the Elasticsearch analyzer must agree on abbreviations, or search and scoring diverge. */
    @Test
    void abbreviationsMatchElasticsearchSynonymFilter() throws Exception {
        JsonNode synonyms;
        try (InputStream in = getClass().getResourceAsStream("/elasticsearch/watchlist-index.json")) {
            synonyms = new JsonMapper().readTree(in)
                    .at("/settings/analysis/filter/name_abbreviations/synonyms");
        }
        Map<String, String> fromElasticsearch = new HashMap<>();
        for (JsonNode rule : synonyms) {
            String[] sides = rule.asString().split("=>");
            String canonical = sides[1].trim();
            for (String word : sides[0].split(",")) {
                if (!word.trim().equals(canonical)) {
                    fromElasticsearch.put(word.trim(), canonical);
                }
            }
        }
        assertThat(fromElasticsearch).isEqualTo(NameNormalizer.ABBREVIATIONS);
    }
}
