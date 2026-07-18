package dev.iseif.listingquality.enrichment.model.book;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

import java.util.List;

public record GeneratedFieldProposal(
    @NotNull BookField field,
    @NotNull @Valid GeneratedBookValue proposedValue,
    @NotEmpty List<@Valid EvidenceReference> evidence) {

  public GeneratedFieldProposal {
    evidence = evidence == null ? List.of() : List.copyOf(evidence);
  }

  public GeneratedFieldProposal(
      BookField field, JsonNode proposedValue, List<EvidenceReference> evidence) {
    this(field, GeneratedBookValue.from(proposedValue), evidence);
  }
}
