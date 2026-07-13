package dev.iseif.listingquality.service.exception;

public class InvalidAiResponseException extends RuntimeException {

  public InvalidAiResponseException() {
    super("AI provider returned an invalid listing review");
  }

  public InvalidAiResponseException(Throwable cause) {
    super("AI provider returned an invalid listing review", cause);
  }
}
