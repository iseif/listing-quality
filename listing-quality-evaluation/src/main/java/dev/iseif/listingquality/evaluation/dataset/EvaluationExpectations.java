package dev.iseif.listingquality.evaluation.dataset;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record EvaluationExpectations(
    @NotEmpty List<@NotBlank String> expectedConcepts,
    @NotNull List<@NotBlank String> forbiddenClaims,
    boolean requiresHumanReview,
    @NotNull @Valid ScoreRange acceptableScore) {}
