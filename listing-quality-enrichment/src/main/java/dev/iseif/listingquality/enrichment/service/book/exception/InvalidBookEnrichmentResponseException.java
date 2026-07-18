package dev.iseif.listingquality.enrichment.service.book.exception;

public final class InvalidBookEnrichmentResponseException extends RuntimeException {

  private static final String UNKNOWN_FIELD = "UNKNOWN";

  private final BookEnrichmentValidationFailure failure;
  private final String field;

  public InvalidBookEnrichmentResponseException() {
    this(BookEnrichmentValidationFailure.UNCLASSIFIED, UNKNOWN_FIELD, null);
  }

  public InvalidBookEnrichmentResponseException(Throwable cause) {
    this(
        cause instanceof InvalidBookEnrichmentResponseException invalid
            ? invalid.failure()
            : BookEnrichmentValidationFailure.UNCLASSIFIED,
        cause instanceof InvalidBookEnrichmentResponseException invalid
            ? invalid.field()
            : UNKNOWN_FIELD,
        cause);
  }

  public InvalidBookEnrichmentResponseException(
      BookEnrichmentValidationFailure failure, Enum<?> field) {
    this(failure, field == null ? UNKNOWN_FIELD : field.name(), null);
  }

  private InvalidBookEnrichmentResponseException(
      BookEnrichmentValidationFailure failure, String field, Throwable cause) {
    super("AI book enrichment response is invalid", cause);
    this.failure = failure;
    this.field = field;
  }

  public BookEnrichmentValidationFailure failure() {
    return failure;
  }

  public String field() {
    return field;
  }
}
