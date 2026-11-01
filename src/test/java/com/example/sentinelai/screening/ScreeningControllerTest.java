package com.example.sentinelai.screening;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.sentinelai.domain.EntityType;
import com.example.sentinelai.domain.ScreeningQuery;
import com.example.sentinelai.domain.WatchlistRecord;
import com.example.sentinelai.scoring.MatchResult;
import com.example.sentinelai.scoring.MatchSignal;
import com.example.sentinelai.search.SearchUnavailableException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Web-layer slice: real DispatcherServlet, JSON (de)serialization, validation and exception handler;
 * the service is a Mockito mock, so no Elasticsearch or database is needed.
 */
@WebMvcTest(ScreeningController.class)
class ScreeningControllerTest {

    @Autowired MockMvcTester mvc;
    @MockitoBean ScreeningService screeningService;

    private static final WatchlistRecord PETROV = new WatchlistRecord("WL-0001", "Viktor Alekseevich Petrov",
            List.of("Viktor Petrov"), EntityType.PERSON, "RU", List.of("P-RU-7701234"), "SYN-SANCTIONS", "Synthetic.");

    @Test
    void returnsScoredCandidatesWithEvidenceAndSeparateSearchRelevance() {
        var match = new MatchResult(80, MatchResult.Band.HIGH, "Viktor Petrov", List.of(
                new MatchSignal(MatchSignal.Type.NAME_EXACT, 70, "Exact match to alias 'Viktor Petrov'"),
                new MatchSignal(MatchSignal.Type.COUNTRY_MATCH, 5, "Country agrees (RU)"),
                new MatchSignal(MatchSignal.Type.ENTITY_TYPE_MATCH, 5, "Both are person")));
        given(screeningService.screen(any())).willReturn(List.of(new ScoredCandidate(PETROV, 12.5, match)));

        var result = mvc.post().uri("/api/screenings").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "  Viktor Petrov ", "entityType": "PERSON", "country": "ru", "identifier": ""}
                        """)
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.query.country").isEqualTo("RU");
        assertThat(result).bodyJson().extractingPath("$.candidates[0].watchlistId").isEqualTo("WL-0001");
        assertThat(result).bodyJson().extractingPath("$.candidates[0].matchScore").isEqualTo(80);
        assertThat(result).bodyJson().extractingPath("$.candidates[0].band").isEqualTo("HIGH");
        assertThat(result).bodyJson().extractingPath("$.candidates[0].signals[0].type").isEqualTo("NAME_EXACT");
        assertThat(result).bodyJson().extractingPath("$.candidates[0].searchRelevance").isEqualTo(12.5);

        // Input is cleaned before it reaches the service: trimmed, upper-cased country, blank identifier -> null.
        verify(screeningService).screen(new ScreeningQuery("Viktor Petrov", EntityType.PERSON, "RU", null));
    }

    @Test
    void rejectsInvalidInputWithFieldErrors() {
        var result = mvc.post().uri("/api/screenings").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": " ", "country": "Russia"}
                        """)
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.errors.name").isEqualTo("must not be blank");
        assertThat(result).bodyJson().extractingPath("$.errors.entityType").isEqualTo("must not be null");
        assertThat(result).bodyJson().extractingPath("$.errors.country").isEqualTo("must be a 2-letter ISO country code");
        verifyNoInteractions(screeningService);
    }

    @Test
    void rejectsUnknownEntityType() {
        var result = mvc.post().uri("/api/screenings").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Viktor Petrov", "entityType": "VESSEL"}
                        """)
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(screeningService);
    }

    @Test
    void returns503WhenSearchIsUnavailable() {
        given(screeningService.screen(any()))
                .willThrow(new SearchUnavailableException("connection refused", new RuntimeException()));

        var result = mvc.post().uri("/api/screenings").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Viktor Petrov", "entityType": "PERSON"}
                        """)
                .exchange();

        assertThat(result).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Search unavailable");
    }
}
