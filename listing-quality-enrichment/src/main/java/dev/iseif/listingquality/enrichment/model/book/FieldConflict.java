package dev.iseif.listingquality.enrichment.model.book;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

import java.util.List;

public record FieldConflict(
    @NotNull BookField field,
    @NotNull JsonNode sellerValue,
    @NotNull JsonNode catalogValue,
    @NotEmpty List<@Valid Evidence> evidence,
    @NotNull ConflictResolution resolution) {

  public FieldConflict {
    evidence = evidence == null ? List.of() : List.copyOf(evidence);
  }
}
