package dev.iseif.listingquality.enrichment.catalog.book;

import dev.iseif.listingquality.enrichment.model.book.EnrichmentWarning;
import dev.iseif.listingquality.enrichment.model.book.MatchType;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class BookCatalogTools {

  private static final int MAX_SEARCH_RESULTS = 3;

  private final BookCatalogClient catalogClient;
  private final BookCatalogEvidenceLedger ledger;

  public BookCatalogTools(
      BookCatalogClient catalogClient,
      BookCatalogEvidenceLedger ledger) {
    this.catalogClient = catalogClient;
    this.ledger = ledger;
  }

  @Tool(description = "Find one book by a seller-provided ISBN. The application validates the ISBN and uses a fixed Google Books endpoint.")
  public CatalogLookupResult findBookByIsbn(
      @ToolParam(description = "ISBN-10 or ISBN-13, with or without hyphens") String isbn) {
    return Isbn.normalize(isbn)
        .map(this::lookupIsbn)
        .orElseGet(() -> CatalogLookupResult.empty(CatalogLookupStatus.INVALID_QUERY));
  }

  @Tool(description = "Search for up to three books by seller-provided title and author. Ambiguous results cannot support factual proposals.")
  public CatalogLookupResult searchBooks(
      @ToolParam(description = "Book title from the seller listing") String title,
      @ToolParam(description = "Author from the seller listing, or an empty string") String author) {
    String normalizedTitle = normalizeText(title);
    if (normalizedTitle.isBlank()) {
      return CatalogLookupResult.empty(CatalogLookupStatus.INVALID_QUERY);
    }
    String normalizedAuthor = normalizePerson(author);
    String key = "title:" + normalizedTitle + "|author:" + normalizedAuthor;
    return ledger.cached(key).orElseGet(() -> searchAndRecord(
        key, title.trim(), author == null ? "" : author.trim(), normalizedTitle, normalizedAuthor));
  }

  private CatalogLookupResult lookupIsbn(String normalizedIsbn) {
    String key = "isbn:" + normalizedIsbn;
    return ledger.cached(key).orElseGet(() -> {
      try {
        CatalogLookupResult result = catalogClient.findByIsbn(normalizedIsbn)
            .map(found -> CatalogLookupResult.found(MatchType.EXACT_ISBN, List.of(found)))
            .orElseGet(() -> CatalogLookupResult.empty(CatalogLookupStatus.NOT_FOUND));
        ledger.recordLookup(key, result);
        return result;
      } catch (BookCatalogUnavailableException _) {
        return unavailable(key);
      }
    });
  }

  private CatalogLookupResult searchAndRecord(
      String key,
      String title,
      String author,
      String normalizedTitle,
      String normalizedAuthor) {
    try {
      List<BookCatalogRecord> records = catalogClient.search(title, author, MAX_SEARCH_RESULTS);
      CatalogLookupResult result;
      if (records.isEmpty()) {
        result = CatalogLookupResult.empty(CatalogLookupStatus.NOT_FOUND);
      } else if (records.size() == 1
          && exactTitleAndAuthor(records.getFirst(), normalizedTitle, normalizedAuthor)) {
        result = CatalogLookupResult.found(MatchType.EXACT_TITLE_AND_AUTHOR, records);
      } else {
        result = CatalogLookupResult.ambiguous(records);
      }
      ledger.recordLookup(key, result);
      return result;
    } catch (BookCatalogUnavailableException _) {
      return unavailable(key);
    }
  }

  private CatalogLookupResult unavailable(String key) {
    ledger.warning(EnrichmentWarning.CATALOG_LOOKUP_UNAVAILABLE);
    CatalogLookupResult result = CatalogLookupResult.empty(CatalogLookupStatus.UNAVAILABLE);
    ledger.recordLookup(key, result);
    return result;
  }

  private boolean exactTitleAndAuthor(
      BookCatalogRecord catalogRecord, String normalizedTitle, String normalizedAuthor) {
    if (!normalizeText(catalogRecord.title()).equals(normalizedTitle)) {
      return false;
    }
    if (normalizedAuthor.isBlank()) {
      return false;
    }
    return catalogRecord.authors().stream()
        .map(BookCatalogTools::normalizePerson)
        .anyMatch(normalizedAuthor::equals);
  }

  private static String normalizeText(String value) {
    if (value == null) {
      return "";
    }
    String decomposed = Normalizer.normalize(value, Normalizer.Form.NFKD)
        .toLowerCase(Locale.ROOT)
        .replaceAll("\\p{M}", "");
    return decomposed.replaceAll("[^\\p{L}\\p{N}]+", " ").trim().replaceAll("\\s+", " ");
  }

  private static String normalizePerson(String value) {
    return Arrays.stream(normalizeText(value).split(" "))
        .filter(token -> token.length() > 1)
        .reduce((left, right) -> left + " " + right)
        .orElse("");
  }
}
