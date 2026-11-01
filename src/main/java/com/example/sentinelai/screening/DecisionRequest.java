package com.example.sentinelai.screening;

import com.example.sentinelai.review.ReviewDecision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** JSON body of {@code POST /api/screenings/{caseId}/candidates/{candidateId}/decisions}. */
public record DecisionRequest(@NotNull ReviewDecision.Decision decision, @Size(max = 2000) String note) {
}
