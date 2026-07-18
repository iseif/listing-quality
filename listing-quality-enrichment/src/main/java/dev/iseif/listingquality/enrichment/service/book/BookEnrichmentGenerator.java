package dev.iseif.listingquality.enrichment.service.book;

import dev.iseif.listingquality.enrichment.catalog.book.BookCatalogTools;
import dev.iseif.listingquality.enrichment.model.book.GeneratedBookEnrichment;

@FunctionalInterface
public interface BookEnrichmentGenerator {

  GeneratedBookEnrichment generate(String prompt, BookCatalogTools tools);
}
