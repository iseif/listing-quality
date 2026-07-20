package dev.iseif.listingquality.enrichment.media;

import java.util.List;

public record ProductImageLoadResult(
    List<ProductImage> images,
    List<ImageLoadWarning> warnings) {

  public ProductImageLoadResult {
    images = images == null ? List.of() : List.copyOf(images);
    warnings = warnings == null ? List.of() : List.copyOf(warnings);
  }
}
