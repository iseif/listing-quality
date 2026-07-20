package dev.iseif.listingquality.enrichment.service.shoe;

import dev.iseif.listingquality.enrichment.media.ProductImageLoadResult;
import dev.iseif.listingquality.enrichment.media.ProductImageLoader;
import dev.iseif.listingquality.enrichment.model.shoe.ShoeColorEnrichmentRequest;
import dev.iseif.listingquality.enrichment.model.shoe.ShoeColorEnrichmentResponse;
import dev.iseif.listingquality.enrichment.model.shoe.ShoeColorWarning;
import dev.iseif.listingquality.enrichment.prompt.ShoeColorPrompt;

import java.util.List;

public final class ShoeColorEnrichmentService {

  private final ProductImageLoader imageLoader;
  private final ShoeColorPrompt prompt;
  private final FailoverShoeColorGenerator failover;
  private final ShoeColorComparisonPolicy comparisonPolicy;

  public ShoeColorEnrichmentService(
      ProductImageLoader imageLoader,
      ShoeColorPrompt prompt,
      FailoverShoeColorGenerator failover,
      ShoeColorComparisonPolicy comparisonPolicy) {
    this.imageLoader = imageLoader;
    this.prompt = prompt;
    this.failover = failover;
    this.comparisonPolicy = comparisonPolicy;
  }

  public ShoeColorEnrichmentResponse enrich(ShoeColorEnrichmentRequest request) {
    ProductImageLoadResult loaded = imageLoader.load(request.imageUrls());
    String renderedPrompt = prompt.render(loaded.images());
    ShoeColorExecution execution = failover.execute(renderedPrompt, loaded.images());
    // An exhaustive switch keeps the media vocabulary out of the API contract while making a new
    // load warning a compile error here rather than a silently dropped detail.
    List<ShoeColorWarning> warnings = loaded.warnings().stream()
        .map(warning -> switch (warning) {
          case IMAGE_UNAVAILABLE -> ShoeColorWarning.IMAGE_UNAVAILABLE;
        })
        .toList();
    return comparisonPolicy.compare(
        request,
        execution.extraction(),
        warnings,
        execution.route());
  }
}
