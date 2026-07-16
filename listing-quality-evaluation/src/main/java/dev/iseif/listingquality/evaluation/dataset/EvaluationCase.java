package dev.iseif.listingquality.evaluation.dataset;

import dev.iseif.listingquality.evaluation.api.ListingDraftPayload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record EvaluationCase(
    @NotBlank String id,
    @NotEmpty Set<@NotBlank String> tags,
    @NotNull @Valid ListingDraftPayload listing,
    @NotNull @Valid EvaluationExpectations expectations) {}
