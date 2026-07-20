package dev.iseif.listingquality.enrichment.model.shoe;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record IgnoredShoeImage(
    @NotBlank String imageId,
    @NotNull ShoeImageAssessmentReason reason) {
}
