package dev.iseif.listingquality.enrichment.model.shoe;

import dev.iseif.listingquality.enrichment.model.ExecutionMetadata;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ShoeColorEnrichmentResponse(
    @NotNull ShoeColorStatus status,
    @NotNull List<@Valid ObservedShoeColor> observedColors,
    @NotNull List<@Valid IgnoredShoeImage> ignoredImages,
    @NotNull List<@Valid ShoeColorProposal> proposals,
    @NotNull List<@Valid ShoeColorConflict> conflicts,
    @NotNull List<@NotNull ShoeColorWarning> warnings,
    boolean requiresSellerApproval,
    @NotNull @Valid ExecutionMetadata execution) {

  public ShoeColorEnrichmentResponse {
    observedColors = observedColors == null ? List.of() : List.copyOf(observedColors);
    ignoredImages = ignoredImages == null ? List.of() : List.copyOf(ignoredImages);
    proposals = proposals == null ? List.of() : List.copyOf(proposals);
    conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
    warnings = warnings == null ? List.of() : List.copyOf(warnings);
  }
}
