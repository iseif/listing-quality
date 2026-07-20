package dev.iseif.listingquality.enrichment.live;

import dev.iseif.listingquality.enrichment.media.ProductImage;
import dev.iseif.listingquality.enrichment.model.shoe.ShoeColor;
import dev.iseif.listingquality.enrichment.model.shoe.ShoeColorOutcome;

import java.util.List;
import java.util.Set;

record ShoeColorQualificationCase(
    String id,
    List<ProductImage> images,
    ShoeColorOutcome expectedOutcome,
    ShoeColor expectedPrimary,
    Set<ShoeColor> expectedColors,
    Set<String> expectedIgnoredImageIds,
    Set<ShoeColor> forbiddenColors,
    boolean outsoleNegative,
    boolean backgroundPropNegative) {

  ShoeColorQualificationCase {
    images = List.copyOf(images);
    expectedColors = Set.copyOf(expectedColors);
    expectedIgnoredImageIds = Set.copyOf(expectedIgnoredImageIds);
    forbiddenColors = Set.copyOf(forbiddenColors);
  }
}
