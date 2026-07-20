package dev.iseif.listingquality.enrichment.model.shoe;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ObservedShoeColor(
    @NotNull ShoeColor color,
    @NotNull ShoeColorRole role,
    @NotNull List<@NotBlank String> evidenceImageIds) {

  public ObservedShoeColor {
    evidenceImageIds = evidenceImageIds == null ? List.of() : List.copyOf(evidenceImageIds);
  }
}
