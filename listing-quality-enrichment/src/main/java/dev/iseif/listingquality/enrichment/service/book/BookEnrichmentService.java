package dev.iseif.listingquality.enrichment.service.book;

import dev.iseif.listingquality.enrichment.catalog.book.BookCatalogClient;
import dev.iseif.listingquality.enrichment.catalog.book.BookCatalogEvidenceLedger;
import dev.iseif.listingquality.enrichment.catalog.book.BookCatalogTools;
import dev.iseif.listingquality.enrichment.model.book.BookEnrichmentRequest;
import dev.iseif.listingquality.enrichment.model.book.BookEnrichmentResponse;
import dev.iseif.listingquality.enrichment.prompt.BookEnrichmentPrompt;
import org.springframework.stereotype.Service;

@Service
public final class BookEnrichmentService {

  private final BookCatalogClient catalogClient;
  private final BookEnrichmentPrompt prompt;
  private final FailoverBookEnrichmentGenerator failover;

  public BookEnrichmentService(
      BookCatalogClient catalogClient,
      BookEnrichmentPrompt prompt,
      FailoverBookEnrichmentGenerator failover) {
    this.catalogClient = catalogClient;
    this.prompt = prompt;
    this.failover = failover;
  }

  public BookEnrichmentResponse enrich(BookEnrichmentRequest request) {
    BookCatalogEvidenceLedger ledger = new BookCatalogEvidenceLedger();
    BookCatalogTools tools = new BookCatalogTools(catalogClient, ledger);
    String renderedPrompt = prompt.render(request);
    BookEnrichmentExecution execution = failover.execute(request, renderedPrompt, tools, ledger);
    return execution.toResponse(ledger.warnings());
  }
}
