package com.example.sentinelai.review;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseCandidateRepository extends JpaRepository<CaseCandidate, Long> {

    /** Derived query: WHERE id = ? AND case_id = ? — a candidate id from another case is "not found". */
    Optional<CaseCandidate> findByIdAndScreeningCaseId(Long id, Long caseId);
}
