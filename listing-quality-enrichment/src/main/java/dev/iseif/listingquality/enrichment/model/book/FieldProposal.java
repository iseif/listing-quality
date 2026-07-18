package dev.iseif.listingquality.enrichment.model.book;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

import java.util.List;

public record FieldProposal(
    @NotNull BookField field,
    JsonNode currentValue,
    @NotNull JsonNode proposedValue,
    @NotEmpty List<@Valid Evidence> evidence) {

  public FieldProposal {
    evidence = evidence == null ? List.of() : List.copyOf(evidence);
  }
}
