package com.example.sentinelai.review;

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
import jakarta.persistence.Table;
import java.time.Instant;

/** Append-only audit entry: one analyst decision on one candidate. Never updated or deleted by the app. */
@Entity
@Table(name = "review_decision")
public class ReviewDecision {

    public enum Decision { REVIEWED, DISMISSED, ESCALATED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id")
    private CaseCandidate candidate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Decision decision;

    private String note;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    protected ReviewDecision() {
    }

    ReviewDecision(CaseCandidate candidate, Decision decision, String note, Instant decidedAt) {
        this.candidate = candidate;
        this.decision = decision;
        this.note = note;
        this.decidedAt = decidedAt;
    }

    public Long getId() { return id; }
    public Decision getDecision() { return decision; }
    public String getNote() { return note; }
    public Instant getDecidedAt() { return decidedAt; }
}
