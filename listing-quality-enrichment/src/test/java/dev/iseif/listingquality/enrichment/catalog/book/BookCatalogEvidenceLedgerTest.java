package dev.iseif.listingquality.enrichment.catalog.book;

import dev.iseif.listingquality.enrichment.model.book.EnrichmentWarning;
import dev.iseif.listingquality.enrichment.model.book.MatchType;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookCatalogEvidenceLedgerTest {

  @Test
  void cachesLookupsAndIndexesEvidenceByRecordId() {
    BookCatalogEvidenceLedger ledger = new BookCatalogEvidenceLedger();
    BookCatalogRecord first = catalogRecord("volume-1", "Prentice Hall");
    CatalogLookupResult result = CatalogLookupResult.found(MatchType.EXACT_ISBN, List.of(first));

    ledger.recordLookup("isbn:9780132350884", result);

    assertThat(ledger.cached("isbn:9780132350884")).contains(result);
    assertThat(ledger.findRecord("volume-1")).contains(first);
    assertThat(ledger.matchType("volume-1")).contains(MatchType.EXACT_ISBN);
  }

  @Test
  void rejectsTheSameRecordIdWithDifferentContent() {
    BookCatalogEvidenceLedger ledger = new BookCatalogEvidenceLedger();
    ledger.recordLookup("isbn:9780132350884", CatalogLookupResult.found(
        MatchType.EXACT_ISBN, List.of(catalogRecord("volume-1", "Prentice Hall"))));

    CatalogLookupResult conflicting = CatalogLookupResult.found(
        MatchType.EXACT_TITLE_AND_AUTHOR,
        List.of(catalogRecord("volume-1", "Different Publisher")));

    assertThatThrownBy(() -> ledger.recordLookup("title:clean code|author:martin", conflicting))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Catalog record ID maps to different content: volume-1");
  }

  @Test
  void exposesImmutableWarningsAndSnapshots() {
    BookCatalogEvidenceLedger ledger = new BookCatalogEvidenceLedger();
    ledger.warning(EnrichmentWarning.CATALOG_LOOKUP_UNAVAILABLE);

    assertThat(ledger.warnings()).containsExactly(EnrichmentWarning.CATALOG_LOOKUP_UNAVAILABLE);
    Set<EnrichmentWarning> warnings = ledger.warnings();

    assertThatThrownBy(warnings::clear).isInstanceOf(UnsupportedOperationException.class);
  }

  private BookCatalogRecord catalogRecord(String id, String publisher) {
    return new BookCatalogRecord(
        id,
        URI.create("https://books.google.com/books?id=" + id),
        "Clean Code",
        null,
        List.of("Robert C. Martin"),
        publisher,
        "2008-08-01",
        "A handbook about writing maintainable software.",
        "0132350882",
        "9780132350884",
        "en",
        464,
        List.of("Computers"));
  }
}
