package com.example.sentinelai.screening;

import com.example.sentinelai.domain.EntityType;
import com.example.sentinelai.domain.ScreeningQuery;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** JSON body of {@code POST /api/screenings}. Validated by {@code @Valid} before the controller method runs. */
public record ScreeningRequest(
        @NotBlank @Size(max = 200) String name,
        @NotNull EntityType entityType,
        @Pattern(regexp = "^$|^[A-Za-z]{2}$", message = "must be a 2-letter ISO country code") String country,
        @Size(max = 100) String identifier) {

    public ScreeningQuery toQuery() {
        return ScreeningQuery.of(name, entityType, country, identifier);
    }
}
