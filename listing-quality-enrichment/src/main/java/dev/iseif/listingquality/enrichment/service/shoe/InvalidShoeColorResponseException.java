package dev.iseif.listingquality.enrichment.service.shoe;

public final class InvalidShoeColorResponseException extends RuntimeException {

  private final ShoeColorValidationFailure failure;
  private final String imageId;

  public InvalidShoeColorResponseException(
      ShoeColorValidationFailure failure,
      String imageId) {
    super("Shoe color output failed validation");
    this.failure = failure;
    this.imageId = imageId;
  }

  public ShoeColorValidationFailure failure() {
    return failure;
  }

  public String imageId() {
    return imageId;
  }
}
