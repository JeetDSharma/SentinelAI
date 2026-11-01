package com.example.sentinelai.review;

import static com.example.sentinelai.domain.EntityType.PERSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.sentinelai.IntegrationTest;
import com.example.sentinelai.domain.ScreeningQuery;
import com.example.sentinelai.domain.WatchlistRecord;
import com.example.sentinelai.review.ReviewDecision.Decision;
import com.example.sentinelai.scoring.MatchResult;
import com.example.sentinelai.scoring.MatchSignal;
import com.example.sentinelai.scoring.ScoredCandidate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Persistence and transaction behaviour against real PostgreSQL. Assertions on database state use plain JDBC,
 * so they see what was actually committed — not Hibernate's in-memory view.
 */
@IntegrationTest
class CaseReviewServiceIT {

    @Autowired CaseReviewService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    private static final ScreeningQuery QUERY = ScreeningQuery.of("Viktor Petrov", PERSON, "RU", null);

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE screening_case RESTART IDENTITY CASCADE");
    }

    private static ScoredCandidate scored(String watchlistId, String name, int score) {
        var record = new WatchlistRecord(watchlistId, name, List.of(), PERSON, "RU", List.of(), "SYN-SANCTIONS",
                "Synthetic.");
        var match = new MatchResult(score, MatchResult.Band.of(score), name, List.of(
                new MatchSignal(MatchSignal.Type.NAME_SIMILAR, score - 10, "Name similarity"),
                new MatchSignal(MatchSignal.Type.COUNTRY_MATCH, 5, "Country agrees (RU)"),
                new MatchSignal(MatchSignal.Type.ENTITY_TYPE_MATCH, 5, "Both are person")));
        return new ScoredCandidate(record, 12.5, match);
    }

    private String candidateStatus(long candidateId) {
        return jdbc.queryForObject("SELECT status FROM case_candidate WHERE id = ?", String.class, candidateId);
    }

    private int decisionRows() {
        return jdbc.queryForObject("SELECT count(*) FROM review_decision", Integer.class);
    }

    @Test
    void createdCaseRoundTripsThroughTheDatabaseIncludingJsonbSignals() {
        CaseDetail created = service.createCase(QUERY,
                List.of(scored("WL-0001", "Viktor Petrov", 80), scored("WL-0011", "Viktor Volkov", 60)));

        assertThat(created.id()).isNotNull();
        assertThat(created.status()).isEqualTo(ScreeningCase.Status.OPEN);
        assertThat(created.candidates()).extracting(CaseDetail.Candidate::id).doesNotContainNull();

        CaseDetail reloaded = service.getCase(created.id());
        assertThat(reloaded).usingRecursiveComparison().isEqualTo(created);
        assertThat(jdbc.queryForObject("SELECT DISTINCT jsonb_typeof(signals) FROM case_candidate", String.class))
                .isEqualTo("array");
    }

    @Test
    void caseWithNoCandidatesIsCompletedImmediately() {
        assertThat(service.createCase(QUERY, List.of()).status()).isEqualTo(ScreeningCase.Status.COMPLETED);
    }

    @Test
    void decisionsUpdateCandidateAppendAuditTrailAndCompleteTheCase() {
        CaseDetail created = service.createCase(QUERY,
                List.of(scored("WL-0001", "Viktor Petrov", 80), scored("WL-0011", "Viktor Volkov", 60)));
        long first = created.candidates().get(0).id();
        long second = created.candidates().get(1).id();

        CaseDetail afterFirst = service.recordDecision(created.id(), first, Decision.ESCALATED, "  Strong match  ");
        assertThat(afterFirst.status()).isEqualTo(ScreeningCase.Status.OPEN); // one candidate still pending
        assertThat(afterFirst.candidates().get(0).decisions()).singleElement()
                .satisfies(d -> assertThat(d.note()).isEqualTo("Strong match"));

        CaseDetail afterSecond = service.recordDecision(created.id(), second, Decision.DISMISSED, "");
        assertThat(afterSecond.status()).isEqualTo(ScreeningCase.Status.COMPLETED);

        assertThat(candidateStatus(first)).isEqualTo("ESCALATED");
        assertThat(candidateStatus(second)).isEqualTo("DISMISSED");
        assertThat(jdbc.queryForObject("SELECT version FROM case_candidate WHERE id = ?", Long.class, first)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT note FROM review_decision WHERE candidate_id = ?", String.class, second))
                .isNull(); // blank note stored as NULL
    }

    @Test
    void changingADecisionKeepsTheFullHistory() {
        CaseDetail created = service.createCase(QUERY, List.of(scored("WL-0001", "Viktor Petrov", 80)));
        long candidate = created.candidates().getFirst().id();

        service.recordDecision(created.id(), candidate, Decision.REVIEWED, "Looks fine");
        CaseDetail updated = service.recordDecision(created.id(), candidate, Decision.ESCALATED, "New information");

        assertThat(updated.candidates().getFirst().status()).isEqualTo(CaseCandidate.Status.ESCALATED);
        assertThat(updated.candidates().getFirst().decisions()).extracting(CaseDetail.Decision::decision)
                .containsExactly(Decision.REVIEWED, Decision.ESCALATED);
    }

    @Test
    void candidateFromAnotherCaseIsNotFoundAndNothingIsWritten() {
        CaseDetail caseA = service.createCase(QUERY, List.of(scored("WL-0001", "Viktor Petrov", 80)));
        CaseDetail caseB = service.createCase(QUERY, List.of(scored("WL-0001", "Viktor Petrov", 80)));
        long candidateOfB = caseB.candidates().getFirst().id();

        assertThatThrownBy(() -> service.recordDecision(caseA.id(), candidateOfB, Decision.DISMISSED, null))
                .isInstanceOf(NotFoundException.class);
        assertThat(decisionRows()).isZero();
        assertThat(candidateStatus(candidateOfB)).isEqualTo("PENDING");
    }

    @Test
    void databaseErrorMidTransactionLeavesNoPartialState() {
        CaseDetail created = service.createCase(QUERY, List.of(scored("WL-0001", "Viktor Petrov", 80)));
        long candidate = created.candidates().getFirst().id();
        String tooLong = "x".repeat(2001); // bypasses web validation; VARCHAR(2000) rejects it

        assertThatThrownBy(() -> service.recordDecision(created.id(), candidate, Decision.ESCALATED, tooLong))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(decisionRows()).isZero();
        assertThat(candidateStatus(candidate)).isEqualTo("PENDING");
        assertThat(service.getCase(created.id()).status()).isEqualTo(ScreeningCase.Status.OPEN);
    }

    /**
     * Two analysts decide on the same candidate at the same time.
     * Transaction A loads the candidate (version 0) and has already INSERTed its decision row. Before A commits,
     * B runs in a separate transaction (REQUIRES_NEW) and commits (version -> 1). A's commit then executes
     * {@code UPDATE ... WHERE version = 0}, which matches 0 rows: A fails and its already-inserted decision row
     * is rolled back. B's decision survives; nothing is silently overwritten.
     */
    @Test
    void concurrentDecisionOnSameCandidateFailsLaterCommitAndRollsItBack() {
        CaseDetail created = service.createCase(QUERY, List.of(scored("WL-0001", "Viktor Petrov", 80)));
        long caseId = created.id();
        long candidate = created.candidates().getFirst().id();

        var transactionA = new TransactionTemplate(transactionManager);
        var transactionB = new TransactionTemplate(transactionManager);
        transactionB.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThatThrownBy(() -> transactionA.executeWithoutResult(status -> {
            service.recordDecision(caseId, candidate, Decision.DISMISSED, "analyst A"); // joins transaction A
            transactionB.executeWithoutResult(s ->
                    service.recordDecision(caseId, candidate, Decision.ESCALATED, "analyst B")); // commits first
        })).isInstanceOf(ObjectOptimisticLockingFailureException.class);

        assertThat(jdbc.queryForList("SELECT note FROM review_decision", String.class)).containsExactly("analyst B");
        assertThat(candidateStatus(candidate)).isEqualTo("ESCALATED");
        assertThat(jdbc.queryForObject("SELECT version FROM case_candidate WHERE id = ?", Long.class, candidate))
                .isEqualTo(1);
    }

    @Test
    void historyIsNewestFirstWithCountsAndRespectsLimit() {
        service.createCase(QUERY, List.of(scored("WL-0001", "Viktor Petrov", 80)));
        CaseDetail middle = service.createCase(ScreeningQuery.of("Li Wei", PERSON, null, null),
                List.of(scored("WL-0007", "Li Wei", 75), scored("WL-0008", "Wei Li", 75)));
        CaseDetail newest = service.createCase(ScreeningQuery.of("Nobody", PERSON, null, null), List.of());
        service.recordDecision(middle.id(), middle.candidates().getFirst().id(), Decision.ESCALATED, null);

        List<CaseSummary> recent = service.recentCases(2);

        assertThat(recent).extracting(CaseSummary::id).containsExactly(newest.id(), middle.id());
        assertThat(recent.get(0)).satisfies(s -> {
            assertThat(s.candidateCount()).isZero();
            assertThat(s.topScore()).isNull();
            assertThat(s.status()).isEqualTo(ScreeningCase.Status.COMPLETED);
        });
        assertThat(recent.get(1)).satisfies(s -> {
            assertThat(s.candidateCount()).isEqualTo(2);
            assertThat(s.topScore()).isEqualTo(75);
            assertThat(s.pendingCount()).isEqualTo(1);
            assertThat(s.escalatedCount()).isEqualTo(1);
        });
    }
}
