package dev.iseif.listingquality.enrichment.model.book;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

import java.util.List;

public record GeneratedDerivedBookAttribute(
    @NotNull BookDiscoveryField field,
    @NotNull @Valid GeneratedBookValue value,
    @NotEmpty List<@Valid EvidenceReference> evidence) {

  public GeneratedDerivedBookAttribute {
    evidence = evidence == null ? List.of() : List.copyOf(evidence);
  }

  public GeneratedDerivedBookAttribute(
      BookDiscoveryField field, JsonNode value, List<EvidenceReference> evidence) {
    this(field, GeneratedBookValue.from(value), evidence);
  }
}
