package dev.iseif.listingquality.enrichment.model.book;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Map;

public record BookEnrichmentRequest(
    @NotBlank @Size(max = 100) String listingId,
    @NotBlank @Size(max = 300) String title,
    @Size(max = 5000) String description,
    @PositiveOrZero BigDecimal price,
    @Size(max = 50) Map<@Size(max = 100) String, @Size(max = 1000) String> attributes) {

  public BookEnrichmentRequest {
    attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
  }
}
