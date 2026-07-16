package dev.iseif.listingquality.evaluation.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Map;

public record ListingDraftPayload(
    @NotBlank @Size(max = 200) String title,
    @NotBlank @Size(max = 5000) String description,
    @NotBlank @Size(max = 100) String category,
    @NotNull @PositiveOrZero BigDecimal price,
    @Size(max = 50) Map<String, String> attributes) {}
