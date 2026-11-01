package com.example.sentinelai.review;

import com.example.sentinelai.domain.EntityType;
import com.example.sentinelai.domain.ScreeningQuery;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One screening request and the candidates shown for it. Aggregate root: candidates are saved through it. */
@Entity
@Table(name = "screening_case")
public class ScreeningCase {

    public enum Status { OPEN, COMPLETED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // BIGSERIAL: the database assigns the id on INSERT
    private Long id;

    @Column(name = "query_name", nullable = false)
    private String queryName;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false)
    private EntityType entityType;

    private String country;
    private String identifier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.OPEN;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // mappedBy: CaseCandidate.screeningCase owns the foreign key. cascade ALL: persisting the case persists candidates.
    @OneToMany(mappedBy = "screeningCase", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("score DESC, id ASC")
    private List<CaseCandidate> candidates = new ArrayList<>();

    protected ScreeningCase() {
        // for JPA: Hibernate instantiates entities reflectively, then sets fields
    }

    public ScreeningCase(ScreeningQuery query, Instant createdAt) {
        this.queryName = query.name();
        this.entityType = query.entityType();
        this.country = query.country();
        this.identifier = query.identifier();
        this.createdAt = createdAt;
    }

    public void addCandidate(CaseCandidate candidate) {
        candidates.add(candidate);
        candidate.attachTo(this);
    }

    /** A case is complete once every candidate has an analyst decision (a case with no candidates is complete). */
    void refreshStatus() {
        boolean anyPending = candidates.stream().anyMatch(c -> c.getStatus() == CaseCandidate.Status.PENDING);
        status = anyPending ? Status.OPEN : Status.COMPLETED;
    }

    public Long getId() { return id; }
    public String getQueryName() { return queryName; }
    public EntityType getEntityType() { return entityType; }
    public String getCountry() { return country; }
    public String getIdentifier() { return identifier; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public List<CaseCandidate> getCandidates() { return Collections.unmodifiableList(candidates); }
}
