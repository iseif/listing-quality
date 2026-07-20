package dev.iseif.listingquality.enrichment.media;

import org.springframework.util.MimeType;

import java.util.Objects;

public record ProductImage(String imageId, MimeType mediaType, byte[] bytes) {

  public ProductImage {
    Objects.requireNonNull(imageId, "imageId must not be null");
    Objects.requireNonNull(mediaType, "mediaType must not be null");
    bytes = Objects.requireNonNull(bytes, "bytes must not be null").clone();
  }

  /**
   * Returns a defensive copy. Callers that only need the payload size should use
   * {@link #byteCount()} instead, because an image can be several megabytes.
   */
  @Override
  public byte[] bytes() {
    return bytes.clone();
  }

  public int byteCount() {
    return bytes.length;
  }
}
