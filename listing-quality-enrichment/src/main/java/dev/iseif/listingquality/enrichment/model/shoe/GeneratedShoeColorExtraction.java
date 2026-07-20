package dev.iseif.listingquality.enrichment.model.shoe;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record GeneratedShoeColorExtraction(
    @NotNull ShoeColorOutcome outcome,
    @NotNull @Size(max = 3) List<@Valid GeneratedObservedShoeColor> colors,
    @NotNull @Size(min = 1, max = 3) List<@Valid GeneratedShoeImageAssessment> imageAssessments) {

  public GeneratedShoeColorExtraction {
    colors = colors == null ? List.of() : List.copyOf(colors);
    imageAssessments = imageAssessments == null ? List.of() : List.copyOf(imageAssessments);
  }
}
