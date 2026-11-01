package com.example.sentinelai.screening;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.sentinelai.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** End to end through HTTP: real controller, real Elasticsearch search, real scoring, real PostgreSQL. */
@IntegrationTest
class ScreeningFlowIT {

    @Autowired MockMvcTester mvc;
    @Autowired JsonMapper json;

    private JsonNode body(MvcTestResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void screenDecideAndRetrieveFromHistory() throws Exception {
        MvcTestResult screened = mvc.post().uri("/api/screenings").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Viktor Petrov", "entityType": "PERSON", "country": "RU"}
                        """)
                .exchange();
        assertThat(screened).hasStatus(HttpStatus.CREATED);
        JsonNode created = body(screened);
        long caseId = created.get("id").asLong();
        JsonNode top = created.get("candidates").get(0);
        assertThat(top.get("watchlistId").asString()).isEqualTo("WL-0001");
        assertThat(top.get("matchScore").asInt()).isEqualTo(80);
        assertThat(top.get("status").asString()).isEqualTo("PENDING");

        MvcTestResult decided = mvc.post()
                .uri("/api/screenings/{caseId}/candidates/{candidateId}/decisions", caseId, top.get("id").asLong())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"decision": "ESCALATED", "note": "Alias, country and type all agree"}
                        """)
                .exchange();
        assertThat(decided).hasStatus(HttpStatus.CREATED);

        MvcTestResult fetched = mvc.get().uri("/api/screenings/{id}", caseId).exchange();
        assertThat(fetched).hasStatusOk();
        assertThat(fetched).bodyJson().extractingPath("$.candidates[0].status").isEqualTo("ESCALATED");
        assertThat(fetched).bodyJson().extractingPath("$.candidates[0].decisions[0].note")
                .isEqualTo("Alias, country and type all agree");

        MvcTestResult history = mvc.get().uri("/api/screenings?limit=5").exchange();
        assertThat(history).hasStatusOk();
        JsonNode newest = body(history).get(0);
        assertThat(newest.get("id").asLong()).isEqualTo(caseId);
        assertThat(newest.get("escalatedCount").asLong()).isEqualTo(1);
    }
}
