package dev.iseif.listingquality.enrichment.media.exception;

public class ImageSourceUnavailableException extends RuntimeException {

  public ImageSourceUnavailableException(String message) {
    super(message);
  }

  public ImageSourceUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
