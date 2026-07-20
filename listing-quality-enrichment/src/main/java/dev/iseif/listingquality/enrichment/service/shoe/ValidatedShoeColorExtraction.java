package dev.iseif.listingquality.enrichment.service.shoe;

import dev.iseif.listingquality.enrichment.model.shoe.IgnoredShoeImage;
import dev.iseif.listingquality.enrichment.model.shoe.ObservedShoeColor;
import dev.iseif.listingquality.enrichment.model.shoe.ShoeColorOutcome;

import java.util.List;

public record ValidatedShoeColorExtraction(
    ShoeColorOutcome outcome,
    List<ObservedShoeColor> observedColors,
    List<IgnoredShoeImage> ignoredImages) {

  public ValidatedShoeColorExtraction {
    observedColors = observedColors == null ? List.of() : List.copyOf(observedColors);
    ignoredImages = ignoredImages == null ? List.of() : List.copyOf(ignoredImages);
  }
}
