package com.example.sentinelai.review;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Spring Data generates the implementation at startup (a JDK proxy) from method names and @Query. */
public interface ScreeningCaseRepository extends JpaRepository<ScreeningCase, Long> {

    /** One query: case LEFT JOIN candidates. Decisions are then batch-loaded (default_batch_fetch_size). */
    @EntityGraph(attributePaths = "candidates")
    Optional<ScreeningCase> findWithCandidatesById(Long id);

    /** History list as a single aggregate query: no entities loaded, no N+1. */
    @Query("""
            select new com.example.sentinelai.review.CaseSummary(
                c.id, c.queryName, c.entityType, c.country, c.status, c.createdAt,
                count(k), max(k.score),
                sum(case when k.status = :pending then 1 else 0 end),
                sum(case when k.status = :escalated then 1 else 0 end))
            from ScreeningCase c left join c.candidates k
            group by c.id, c.queryName, c.entityType, c.country, c.status, c.createdAt
            order by c.createdAt desc, c.id desc
            """)
    List<CaseSummary> findRecentSummaries(CaseCandidate.Status pending, CaseCandidate.Status escalated,
                                          Pageable page);
}
