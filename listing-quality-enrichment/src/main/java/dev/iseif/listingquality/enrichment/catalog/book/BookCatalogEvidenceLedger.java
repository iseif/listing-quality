package dev.iseif.listingquality.enrichment.catalog.book;

import dev.iseif.listingquality.enrichment.model.book.EnrichmentWarning;
import dev.iseif.listingquality.enrichment.model.book.MatchType;

import java.util.*;

public final class BookCatalogEvidenceLedger {

  private final Map<String, CatalogLookupResult> lookups = new LinkedHashMap<>();
  private final Map<String, BookCatalogRecord> records = new LinkedHashMap<>();
  private final Map<String, MatchType> matchTypes = new LinkedHashMap<>();
  private final EnumSet<EnrichmentWarning> warnings = EnumSet.noneOf(EnrichmentWarning.class);

  public Optional<CatalogLookupResult> cached(String key) {
    return Optional.ofNullable(lookups.get(key));
  }

  public void recordLookup(String key, CatalogLookupResult result) {
    CatalogLookupResult existingLookup = lookups.putIfAbsent(key, result);
    if (existingLookup != null && !existingLookup.equals(result)) {
      throw new IllegalArgumentException("Catalog lookup key maps to different results: " + key);
    }
    for (BookCatalogRecord catalogRecord : result.records()) {
      BookCatalogRecord existingRecord = records.putIfAbsent(catalogRecord.recordId(), catalogRecord);
      if (existingRecord != null && !existingRecord.equals(catalogRecord)) {
        throw new IllegalArgumentException(
            "Catalog record ID maps to different content: " + catalogRecord.recordId());
      }
      if (result.matchType() != null) {
        matchTypes.merge(
            catalogRecord.recordId(), result.matchType(), BookCatalogEvidenceLedger::firstUnlessExactIsbn);
      }
    }
  }

  public Optional<BookCatalogRecord> findRecord(String recordId) {
    return Optional.ofNullable(records.get(recordId));
  }

  public Optional<MatchType> matchType(String recordId) {
    return Optional.ofNullable(matchTypes.get(recordId));
  }

  public void warning(EnrichmentWarning warning) {
    warnings.add(warning);
  }

  public Set<EnrichmentWarning> warnings() {
    return Set.copyOf(warnings);
  }

  /**
   * Keeps the match type a record was first recorded with, so a later ambiguous search cannot
   * weaken it and an earlier ambiguous search cannot be silently upgraded. An exact ISBN hit is
   * the one exception: it identifies a single volume regardless of when it was seen.
   */
  private static MatchType firstUnlessExactIsbn(MatchType existing, MatchType recorded) {
    if (existing == MatchType.EXACT_ISBN || recorded == MatchType.EXACT_ISBN) {
      return MatchType.EXACT_ISBN;
    }
    return existing;
  }
}
