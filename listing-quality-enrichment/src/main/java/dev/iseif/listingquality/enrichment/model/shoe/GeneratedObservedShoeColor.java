package dev.iseif.listingquality.enrichment.model.shoe;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record GeneratedObservedShoeColor(
    @NotNull ShoeColor color,
    @NotNull ShoeColorRole role,
    @NotNull @Size(min = 1, max = 3) List<@NotBlank String> evidenceImageIds) {

  public GeneratedObservedShoeColor {
    evidenceImageIds = evidenceImageIds == null ? List.of() : List.copyOf(evidenceImageIds);
  }
}
