package dev.iseif.listingquality.enrichment.catalog.book;

import java.net.URI;
import java.util.List;

public record BookCatalogRecord(
    String recordId,
    URI sourceUrl,
    String title,
    String subtitle,
    List<String> authors,
    String publisher,
    String publishedDate,
    String description,
    String isbn10,
    String isbn13,
    String language,
    Integer pageCount,
    List<String> categories) {

  public BookCatalogRecord {
    authors = authors == null ? List.of() : List.copyOf(authors);
    categories = categories == null ? List.of() : List.copyOf(categories);
  }
}
