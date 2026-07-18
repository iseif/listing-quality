package dev.iseif.listingquality.enrichment.catalog.book;

import java.util.List;
import java.util.Optional;

public interface BookCatalogClient {

  Optional<BookCatalogRecord> findByIsbn(String normalizedIsbn);

  List<BookCatalogRecord> search(String title, String author, int limit);
}
