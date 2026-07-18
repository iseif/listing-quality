package dev.iseif.listingquality.enrichment.catalog.book.google;

final class GoogleBooksRequestException extends RuntimeException {

  private final int statusCode;

  GoogleBooksRequestException(int statusCode) {
    super("Google Books request failed");
    this.statusCode = statusCode;
  }

  int statusCode() {
    return statusCode;
  }
}
