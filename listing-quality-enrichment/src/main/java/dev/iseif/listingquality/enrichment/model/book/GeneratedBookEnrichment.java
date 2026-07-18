package dev.iseif.listingquality.enrichment.model.book;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record GeneratedBookEnrichment(
    @NotNull List<@Valid GeneratedFieldProposal> proposals,
    @NotNull List<@Valid GeneratedFieldConflict> conflicts,
    @NotNull List<@Valid GeneratedDerivedBookAttribute> derivedAttributes,
    @NotNull List<@NotNull BookField> unresolvedFields) {

  public GeneratedBookEnrichment {
    proposals = proposals == null ? List.of() : List.copyOf(proposals);
    conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
    derivedAttributes = derivedAttributes == null ? List.of() : List.copyOf(derivedAttributes);
    unresolvedFields = unresolvedFields == null ? List.of() : List.copyOf(unresolvedFields);
  }
}
