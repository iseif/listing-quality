package dev.iseif.listingquality.enrichment.catalog.book;

import dev.iseif.listingquality.enrichment.model.book.MatchType;

import java.util.List;

public record CatalogLookupResult(
    CatalogLookupStatus status,
    MatchType matchType,
    List<BookCatalogRecord> records) {

  public CatalogLookupResult {
    records = records == null ? List.of() : List.copyOf(records);
  }

  public static CatalogLookupResult found(
      MatchType matchType, List<BookCatalogRecord> records) {
    if (matchType == null || records == null || records.isEmpty()) {
      throw new IllegalArgumentException("Found catalog results require a match type and records");
    }
    return new CatalogLookupResult(CatalogLookupStatus.FOUND, matchType, records);
  }

  public static CatalogLookupResult empty(CatalogLookupStatus status) {
    if (status == CatalogLookupStatus.FOUND || status == CatalogLookupStatus.AMBIGUOUS) {
      throw new IllegalArgumentException("Found catalog results cannot be empty");
    }
    return new CatalogLookupResult(status, null, List.of());
  }

  public static CatalogLookupResult ambiguous(List<BookCatalogRecord> records) {
    if (records == null || records.isEmpty()) {
      throw new IllegalArgumentException("Ambiguous catalog results require records");
    }
    return new CatalogLookupResult(CatalogLookupStatus.AMBIGUOUS, null, records);
  }
}
