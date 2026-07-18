package dev.iseif.listingquality.enrichment.model.book;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.net.URI;

public record Evidence(
    @NotNull EvidenceSource source,
    String recordId,
    @NotBlank String sourceField,
    URI sourceUrl,
    @NotNull MatchType matchType) {
}
