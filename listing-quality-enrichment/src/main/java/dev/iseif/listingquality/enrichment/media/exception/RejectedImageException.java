package dev.iseif.listingquality.enrichment.media.exception;

public class RejectedImageException extends RuntimeException {

  public RejectedImageException(String message) {
    super(message);
  }

  public RejectedImageException(String message, Throwable cause) {
    super(message, cause);
  }
}
