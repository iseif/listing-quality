package dev.iseif.listingquality.enrichment.media;

import java.net.URI;
import java.util.List;

public interface ProductImageLoader {

  ProductImageLoadResult load(List<URI> imageUrls);
}
