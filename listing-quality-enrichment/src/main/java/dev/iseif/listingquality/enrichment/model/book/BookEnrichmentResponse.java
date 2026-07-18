package dev.iseif.listingquality.enrichment.model.book;

import dev.iseif.listingquality.enrichment.model.ExecutionMetadata;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record BookEnrichmentResponse(
    @NotNull EnrichmentStatus status,
    @NotNull List<@Valid FieldProposal> proposals,
    @NotNull List<@Valid FieldConflict> conflicts,
    @NotNull List<@Valid DerivedBookAttribute> derivedAttributes,
    @NotNull List<@NotNull BookField> unresolvedFields,
    @NotNull List<@NotNull EnrichmentWarning> warnings,
    boolean requiresSellerApproval,
    @NotNull @Valid ExecutionMetadata execution) {

  public BookEnrichmentResponse {
    proposals = proposals == null ? List.of() : List.copyOf(proposals);
    conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
    derivedAttributes = derivedAttributes == null ? List.of() : List.copyOf(derivedAttributes);
    unresolvedFields = unresolvedFields == null ? List.of() : List.copyOf(unresolvedFields);
    warnings = warnings == null ? List.of() : List.copyOf(warnings);
  }
}
