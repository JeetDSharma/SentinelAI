package com.example.sentinelai.review;

import com.example.sentinelai.domain.EntityType;
import com.example.sentinelai.domain.WatchlistRecord;
import com.example.sentinelai.scoring.MatchSignal;
import com.example.sentinelai.scoring.ScoredCandidate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A watchlist record shown to the analyst for a case: snapshot of the evidence + current review status. */
@Entity
@Table(name = "case_candidate")
public class CaseCandidate {

    public enum Status { PENDING, REVIEWED, DISMISSED, ESCALATED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false) // LAZY: don't load the case unless asked
    @JoinColumn(name = "case_id")
    private ScreeningCase screeningCase;

    @Column(name = "watchlist_id", nullable = false)
    private String watchlistId;

    @Column(name = "record_name", nullable = false)
    private String recordName;

    @Enumerated(EnumType.STRING)
    @Column(name = "record_entity_type", nullable = false)
    private EntityType recordEntityType;

    @Column(name = "record_country")
    private String recordCountry;

    private String program;
    private String remarks;

    @Column(name = "matched_name", nullable = false)
    private String matchedName;

    @Column(nullable = false)
    private int score;

    /** Stored as Postgres JSONB; Hibernate serializes the list with Jackson. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<MatchSignal> signals;

    @Column(name = "search_relevance", nullable = false)
    private double searchRelevance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    /**
     * Optimistic locking: Hibernate issues {@code UPDATE ... SET version = v+1 WHERE id = ? AND version = v}.
     * If another transaction already bumped the version, 0 rows match and the commit fails instead of
     * silently overwriting the other analyst's decision (lost update).
     */
    @Version
    private long version;

    @OneToMany(mappedBy = "candidate") // inserted explicitly via ReviewDecisionRepository
    @OrderBy("decidedAt ASC, id ASC")
    private List<ReviewDecision> decisions = new ArrayList<>();

    protected CaseCandidate() {
    }

    public static CaseCandidate from(ScoredCandidate scored) {
        WatchlistRecord record = scored.record();
        CaseCandidate c = new CaseCandidate();
        c.watchlistId = record.id();
        c.recordName = record.name();
        c.recordEntityType = record.entityType();
        c.recordCountry = record.country();
        c.program = record.program();
        c.remarks = record.remarks();
        c.matchedName = scored.match().matchedName();
        c.score = scored.match().score();
        c.signals = List.copyOf(scored.match().signals());
        c.searchRelevance = scored.searchScore();
        return c;
    }

    void attachTo(ScreeningCase screeningCase) {
        this.screeningCase = screeningCase;
    }

    /** Appends to the audit trail and moves the candidate to the decided status. Persisted by cascade. */
    ReviewDecision decide(ReviewDecision.Decision decision, String note, Instant at) {
        ReviewDecision entry = new ReviewDecision(this, decision, note, at);
        decisions.add(entry);
        status = Status.valueOf(decision.name());
        return entry;
    }

    public Long getId() { return id; }
    public ScreeningCase getScreeningCase() { return screeningCase; }
    public String getWatchlistId() { return watchlistId; }
    public String getRecordName() { return recordName; }
    public EntityType getRecordEntityType() { return recordEntityType; }
    public String getRecordCountry() { return recordCountry; }
    public String getProgram() { return program; }
    public String getRemarks() { return remarks; }
    public String getMatchedName() { return matchedName; }
    public int getScore() { return score; }
    public List<MatchSignal> getSignals() { return signals; }
    public double getSearchRelevance() { return searchRelevance; }
    public Status getStatus() { return status; }
    public long getVersion() { return version; }
    public List<ReviewDecision> getDecisions() { return Collections.unmodifiableList(decisions); }
}
