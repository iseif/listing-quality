package dev.iseif.listingquality.enrichment.model.book;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record EvidenceReference(
    @NotNull EvidenceSource source,
    String recordId,
    @NotBlank String sourceField) {
}
