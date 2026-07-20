package dev.iseif.listingquality.enrichment.media;

import java.net.URI;

@FunctionalInterface
public interface ImageUriValidator {

  void validate(URI uri);
}
