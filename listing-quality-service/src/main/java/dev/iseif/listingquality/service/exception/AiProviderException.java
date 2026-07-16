package dev.iseif.listingquality.service.exception;

public class AiProviderException extends RuntimeException {

  public AiProviderException(Throwable cause) {
    super("AI provider request failed", cause);
  }
}
