package dev.iseif.listingquality.evaluation.dataset;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record DatasetManifest(
    @Min(1) int schemaVersion,
    @NotBlank String datasetVersion,
    @NotBlank String rubricVersion,
    @NotBlank String warmupCaseId) {}
