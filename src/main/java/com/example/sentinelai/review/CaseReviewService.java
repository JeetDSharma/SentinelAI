package com.example.sentinelai.review;

import com.example.sentinelai.domain.ScreeningQuery;
import com.example.sentinelai.scoring.ScoredCandidate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * All PostgreSQL work. Each public method is one short transaction containing only database operations.
 *
 * <p>{@code @Transactional} works because callers get a Spring proxy of this bean, not the bean itself: the
 * proxy opens a transaction, calls the method, then commits — or rolls back on a RuntimeException.
 * That is why this lives in its own bean: a {@code this.method()} call inside one class bypasses the proxy.
 */
@Service
public class CaseReviewService {

    private final ScreeningCaseRepository cases;
    private final CaseCandidateRepository candidates;
    private final ReviewDecisionRepository decisions;

    public CaseReviewService(ScreeningCaseRepository cases, CaseCandidateRepository candidates,
                             ReviewDecisionRepository decisions) {
        this.cases = cases;
        this.candidates = candidates;
        this.decisions = decisions;
    }

    /** Saves the case and all candidate snapshots atomically: all rows or none. */
    @Transactional
    public CaseDetail createCase(ScreeningQuery query, List<ScoredCandidate> scored) {
        ScreeningCase screeningCase = new ScreeningCase(query, now());
        scored.forEach(s -> screeningCase.addCandidate(CaseCandidate.from(s)));
        screeningCase.refreshStatus(); // nothing to review -> COMPLETED immediately
        cases.save(screeningCase);     // cascades to candidates; IDENTITY ids force immediate INSERTs
        return CaseDetail.from(screeningCase);
    }

    /**
     * One transaction, three effects: insert the audit row, update the candidate status (version-checked),
     * and complete the case when nothing is pending. Any failure rolls back all three.
     */
    @Transactional
    public CaseDetail recordDecision(long caseId, long candidateId, ReviewDecision.Decision decision, String note) {
        CaseCandidate candidate = candidates.findByIdAndScreeningCaseId(candidateId, caseId)
                .orElseThrow(() -> new NotFoundException(
                        "Candidate " + candidateId + " not found in screening case " + caseId));

        decisions.save(candidate.decide(decision, blankToNull(note), now())); // INSERT now (IDENTITY id)
        ScreeningCase screeningCase = candidate.getScreeningCase();          // lazy-loaded, same transaction
        screeningCase.refreshStatus();
        // No explicit save: candidate and case are managed entities. Hibernate's dirty checking turns their
        // field changes into UPDATE statements when the transaction flushes at commit.
        return CaseDetail.from(screeningCase);
    }

    @Transactional(readOnly = true)
    public CaseDetail getCase(long caseId) {
        return cases.findWithCandidatesById(caseId)
                .map(CaseDetail::from)
                .orElseThrow(() -> new NotFoundException("Screening case " + caseId + " not found"));
    }

    @Transactional(readOnly = true)
    public List<CaseSummary> recentCases(int limit) {
        return cases.findRecentSummaries(CaseCandidate.Status.PENDING, CaseCandidate.Status.ESCALATED,
                PageRequest.of(0, limit));
    }

    /** Postgres timestamptz has microsecond precision; truncate so the API returns what the DB stores. */
    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
