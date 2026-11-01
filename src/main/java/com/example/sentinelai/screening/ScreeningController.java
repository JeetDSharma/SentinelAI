package com.example.sentinelai.screening;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/screenings")
public class ScreeningController {

    private final ScreeningService screeningService;

    public ScreeningController(ScreeningService screeningService) {
        this.screeningService = screeningService;
    }

    @PostMapping
    public ScreeningResponse screen(@Valid @RequestBody ScreeningRequest request) {
        var query = request.toQuery();
        return ScreeningResponse.of(query, screeningService.screen(query));
    }
}
