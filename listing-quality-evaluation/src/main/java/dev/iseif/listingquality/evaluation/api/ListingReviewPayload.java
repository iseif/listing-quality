package dev.iseif.listingquality.evaluation.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ListingReviewPayload(
    @Min(0) @Max(100) int qualityScore,
    @NotNull List<@NotBlank String> missingFields,
    @NotNull List<@NotBlank String> issues,
    @NotNull List<@NotBlank String> suggestions,
    boolean requiresHumanReview) {}
