package dev.iseif.listingquality.enrichment.catalog.book;

public class BookCatalogUnavailableException extends RuntimeException {

  public BookCatalogUnavailableException() {
    super("Google Books catalog is unavailable");
  }

  public BookCatalogUnavailableException(Throwable cause) {
    super("Google Books catalog is unavailable", cause);
  }
}
