package dev.iseif.listingquality.enrichment.model.book;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

import java.util.List;

public record GeneratedFieldConflict(
    @NotNull BookField field,
    @NotNull @Valid GeneratedBookValue catalogValue,
    @NotEmpty List<@Valid EvidenceReference> evidence) {

  public GeneratedFieldConflict {
    evidence = evidence == null ? List.of() : List.copyOf(evidence);
  }

  public GeneratedFieldConflict(
      BookField field, JsonNode catalogValue, List<EvidenceReference> evidence) {
    this(field, GeneratedBookValue.from(catalogValue), evidence);
  }
}
