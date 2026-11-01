package com.example.sentinelai.screening;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.sentinelai.domain.EntityType;
import com.example.sentinelai.domain.ScreeningQuery;
import com.example.sentinelai.review.CaseCandidate;
import com.example.sentinelai.review.CaseDetail;
import com.example.sentinelai.review.CaseReviewService;
import com.example.sentinelai.review.NotFoundException;
import com.example.sentinelai.review.ReviewDecision.Decision;
import com.example.sentinelai.review.ScreeningCase;
import com.example.sentinelai.scoring.MatchResult;
import com.example.sentinelai.scoring.MatchSignal;
import com.example.sentinelai.search.SearchUnavailableException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Web-layer slice: real DispatcherServlet, JSON (de)serialization, validation and exception handler;
 * services are Mockito mocks, so no Elasticsearch or database is needed.
 */
@WebMvcTest(ScreeningController.class)
class ScreeningControllerTest {

    @Autowired MockMvcTester mvc;
    @MockitoBean ScreeningService screeningService;
    @MockitoBean CaseReviewService caseReviewService;

    private static final CaseDetail CASE = new CaseDetail(7L, "Viktor Petrov", EntityType.PERSON, "RU", null,
            ScreeningCase.Status.OPEN, Instant.parse("2026-11-01T10:00:00Z"), List.of(new CaseDetail.Candidate(
                    42L, "WL-0001", "Viktor Alekseevich Petrov", EntityType.PERSON, "RU", "SYN-SANCTIONS", "Synthetic.",
                    "Viktor Petrov", 80, MatchResult.Band.HIGH, List.of(
                            new MatchSignal(MatchSignal.Type.NAME_EXACT, 70, "Exact match to alias 'Viktor Petrov'"),
                            new MatchSignal(MatchSignal.Type.COUNTRY_MATCH, 5, "Country agrees (RU)"),
                            new MatchSignal(MatchSignal.Type.ENTITY_TYPE_MATCH, 5, "Both are person")),
                    12.5, CaseCandidate.Status.PENDING, List.of())));

    private MvcTestResult postJson(String uri, String json) {
        return mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    // ---- POST /api/screenings ----------------------------------------------------------------------

    @Test
    void screeningCreatesCaseAndReturnsScoresEvidenceAndSeparateSearchRelevance() {
        given(screeningService.screen(any())).willReturn(CASE);

        var result = postJson("/api/screenings", """
                {"name": "  Viktor Petrov ", "entityType": "PERSON", "country": "ru", "identifier": ""}
                """);

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).headers().hasValue("Location", "http://localhost/api/screenings/7");
        assertThat(result).bodyJson().extractingPath("$.id").isEqualTo(7);
        assertThat(result).bodyJson().extractingPath("$.candidates[0].id").isEqualTo(42);
        assertThat(result).bodyJson().extractingPath("$.candidates[0].matchScore").isEqualTo(80);
        assertThat(result).bodyJson().extractingPath("$.candidates[0].band").isEqualTo("HIGH");
        assertThat(result).bodyJson().extractingPath("$.candidates[0].signals[0].type").isEqualTo("NAME_EXACT");
        assertThat(result).bodyJson().extractingPath("$.candidates[0].searchRelevance").isEqualTo(12.5);
        assertThat(result).bodyJson().extractingPath("$.createdAt").isEqualTo("2026-11-01T10:00:00Z");

        // Input is cleaned before it reaches the service: trimmed, upper-cased country, blank identifier -> null.
        verify(screeningService).screen(new ScreeningQuery("Viktor Petrov", EntityType.PERSON, "RU", null));
    }

    @Test
    void rejectsInvalidScreeningInputWithFieldErrors() {
        var result = postJson("/api/screenings", """
                {"name": " ", "country": "Russia"}
                """);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.errors.name").isEqualTo("must not be blank");
        assertThat(result).bodyJson().extractingPath("$.errors.entityType").isEqualTo("must not be null");
        assertThat(result).bodyJson().extractingPath("$.errors.country").isEqualTo("must be a 2-letter ISO country code");
        verifyNoInteractions(screeningService);
    }

    @Test
    void rejectsUnknownEntityType() {
        var result = postJson("/api/screenings", """
                {"name": "Viktor Petrov", "entityType": "VESSEL"}
                """);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(screeningService);
    }

    @Test
    void returns503WhenSearchIsUnavailable() {
        given(screeningService.screen(any()))
                .willThrow(new SearchUnavailableException("connection refused", new RuntimeException()));

        var result = postJson("/api/screenings", """
                {"name": "Viktor Petrov", "entityType": "PERSON"}
                """);

        assertThat(result).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Search unavailable");
    }

    // ---- GET history / case -------------------------------------------------------------------------

    @Test
    void historyDefaultsToTwentyAndValidatesLimit() {
        assertThat(mvc.get().uri("/api/screenings").exchange()).hasStatusOk();
        verify(caseReviewService).recentCases(20);

        assertThat(mvc.get().uri("/api/screenings?limit=0").exchange()).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(mvc.get().uri("/api/screenings?limit=500").exchange()).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unknownCaseIs404ProblemDetail() {
        given(caseReviewService.getCase(99L)).willThrow(new NotFoundException("Screening case 99 not found"));

        var result = mvc.get().uri("/api/screenings/99").exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("Screening case 99 not found");
    }

    // ---- POST decision ------------------------------------------------------------------------------

    @Test
    void decisionIsRecordedAndUpdatedCaseReturned() {
        given(caseReviewService.recordDecision(7L, 42L, Decision.ESCALATED, "Strong match")).willReturn(CASE);

        var result = postJson("/api/screenings/7/candidates/42/decisions", """
                {"decision": "ESCALATED", "note": "Strong match"}
                """);

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.id").isEqualTo(7);
    }

    @Test
    void decisionRequiresKnownDecisionAndBoundedNote() {
        assertThat(postJson("/api/screenings/7/candidates/42/decisions", """
                {"note": "no decision"}
                """)).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(postJson("/api/screenings/7/candidates/42/decisions", """
                {"decision": "APPROVED"}
                """)).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(postJson("/api/screenings/7/candidates/42/decisions",
                "{\"decision\": \"REVIEWED\", \"note\": \"" + "x".repeat(2001) + "\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(caseReviewService);
    }

    @Test
    void concurrentUpdateIs409() {
        given(caseReviewService.recordDecision(anyLong(), anyLong(), any(), any()))
                .willThrow(new ObjectOptimisticLockingFailureException(CaseCandidate.class, 42L));

        var result = postJson("/api/screenings/7/candidates/42/decisions", """
                {"decision": "DISMISSED"}
                """);

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Concurrent update");
    }
}
