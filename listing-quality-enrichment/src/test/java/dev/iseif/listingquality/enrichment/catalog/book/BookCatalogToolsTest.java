package dev.iseif.listingquality.enrichment.catalog.book;

import dev.iseif.listingquality.enrichment.model.book.EnrichmentWarning;
import dev.iseif.listingquality.enrichment.model.book.MatchType;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class BookCatalogToolsTest {

  @Test
  void validatesAndCachesIsbnLookups() {
    AtomicInteger calls = new AtomicInteger();
    BookCatalogClient client = new StubClient() {
      @Override
      public Optional<BookCatalogRecord> findByIsbn(String isbn) {
        calls.incrementAndGet();
        return Optional.of(catalogRecord("volume-1", "Clean Code", List.of("Robert C. Martin")));
      }
    };
    BookCatalogEvidenceLedger ledger = new BookCatalogEvidenceLedger();
    BookCatalogTools tools = new BookCatalogTools(client, ledger);

    CatalogLookupResult first = tools.findBookByIsbn("978-0-13-235088-4");
    CatalogLookupResult second = tools.findBookByIsbn("9780132350884");

    assertThat(first).isEqualTo(second);
    assertThat(first.status()).isEqualTo(CatalogLookupStatus.FOUND);
    assertThat(first.matchType()).isEqualTo(MatchType.EXACT_ISBN);
    assertThat(calls).hasValue(1);
  }

  @Test
  void rejectsAnInvalidIsbnWithoutCallingTheCatalog() {
    AtomicInteger calls = new AtomicInteger();
    BookCatalogClient client = new StubClient() {
      @Override
      public Optional<BookCatalogRecord> findByIsbn(String isbn) {
        calls.incrementAndGet();
        return Optional.empty();
      }
    };

    CatalogLookupResult result = new BookCatalogTools(
        client, new BookCatalogEvidenceLedger()).findBookByIsbn("9780132350885");

    assertThat(result.status()).isEqualTo(CatalogLookupStatus.INVALID_QUERY);
    assertThat(calls).hasValue(0);
  }

  @Test
  void marksMultipleTitleResultsAsAmbiguousEvidence() {
    BookCatalogClient client = new StubClient() {
      @Override
      public List<BookCatalogRecord> search(String title, String author, int limit) {
        return List.of(
            catalogRecord("volume-1", "Clean Code", List.of("Robert C. Martin")),
            catalogRecord("volume-2", "Clean Code Workbook", List.of("Robert C. Martin")));
      }
    };
    BookCatalogEvidenceLedger ledger = new BookCatalogEvidenceLedger();

    CatalogLookupResult result = new BookCatalogTools(client, ledger)
        .searchBooks("Clean Code", "Robert Martin");

    assertThat(result.status()).isEqualTo(CatalogLookupStatus.AMBIGUOUS);
    assertThat(result.matchType()).isNull();
    assertThat(ledger.findRecord("volume-1")).isPresent();
    assertThat(ledger.matchType("volume-1")).isEmpty();
  }

  @Test
  void acceptsOneExactTitleAndAuthorMatch() {
    BookCatalogClient client = new StubClient() {
      @Override
      public List<BookCatalogRecord> search(String title, String author, int limit) {
        return List.of(catalogRecord("volume-1", "Clean Code", List.of("Robert C. Martin")));
      }
    };

    CatalogLookupResult result = new BookCatalogTools(
        client, new BookCatalogEvidenceLedger()).searchBooks("Clean Code", "Robert Martin");

    assertThat(result.status()).isEqualTo(CatalogLookupStatus.FOUND);
    assertThat(result.matchType()).isEqualTo(MatchType.EXACT_TITLE_AND_AUTHOR);
  }

  @Test
  void convertsCatalogFailureIntoARequestWarning() {
    BookCatalogClient client = new StubClient() {
      @Override
      public Optional<BookCatalogRecord> findByIsbn(String isbn) {
        throw new BookCatalogUnavailableException();
      }
    };
    BookCatalogEvidenceLedger ledger = new BookCatalogEvidenceLedger();

    CatalogLookupResult result = new BookCatalogTools(client, ledger)
        .findBookByIsbn("9780132350884");

    assertThat(result.status()).isEqualTo(CatalogLookupStatus.UNAVAILABLE);
    assertThat(ledger.warnings()).containsExactly(EnrichmentWarning.CATALOG_LOOKUP_UNAVAILABLE);
  }

  private BookCatalogRecord catalogRecord(String id, String title, List<String> authors) {
    return new BookCatalogRecord(
        id,
        URI.create("https://books.google.com/books?id=" + id),
        title,
        null,
        authors,
        "Prentice Hall",
        "2008-08-01",
        "A handbook about writing maintainable software.",
        "0132350882",
        "9780132350884",
        "en",
        464,
        List.of("Computers"));
  }

  private static class StubClient implements BookCatalogClient {
    @Override
    public Optional<BookCatalogRecord> findByIsbn(String normalizedIsbn) {
      return Optional.empty();
    }

    @Override
    public List<BookCatalogRecord> search(String title, String author, int limit) {
      return List.of();
    }
  }
}
