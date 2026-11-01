package com.example.sentinelai.screening;

import com.example.sentinelai.review.CaseDetail;
import com.example.sentinelai.review.CaseReviewService;
import com.example.sentinelai.review.CaseSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/screenings")
public class ScreeningController {

    private final ScreeningService screeningService;
    private final CaseReviewService caseReviewService;

    public ScreeningController(ScreeningService screeningService, CaseReviewService caseReviewService) {
        this.screeningService = screeningService;
        this.caseReviewService = caseReviewService;
    }

    /** Screens an entity and records the result as a new case. */
    @PostMapping
    public ResponseEntity<CaseDetail> screen(@Valid @RequestBody ScreeningRequest request) {
        CaseDetail created = screeningService.screen(request.toQuery());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    /** Recent screening history, newest first. */
    @GetMapping
    public List<CaseSummary> recent(@RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return caseReviewService.recentCases(limit);
    }

    @GetMapping("/{caseId}")
    public CaseDetail get(@PathVariable long caseId) {
        return caseReviewService.getCase(caseId);
    }

    /** Records an analyst decision; returns the updated case. */
    @PostMapping("/{caseId}/candidates/{candidateId}/decisions")
    public ResponseEntity<CaseDetail> decide(@PathVariable long caseId,
                                             @PathVariable long candidateId,
                                             @Valid @RequestBody DecisionRequest request) {
        CaseDetail updated = caseReviewService.recordDecision(caseId, candidateId, request.decision(), request.note());
        return ResponseEntity.status(201).body(updated);
    }
}
