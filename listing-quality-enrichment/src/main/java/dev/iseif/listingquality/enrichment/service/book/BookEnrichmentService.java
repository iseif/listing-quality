package dev.iseif.listingquality.enrichment.service.book;

import dev.iseif.listingquality.enrichment.catalog.book.BookCatalogClient;
import dev.iseif.listingquality.enrichment.catalog.book.BookCatalogEvidenceLedger;
import dev.iseif.listingquality.enrichment.catalog.book.BookCatalogTools;
import dev.iseif.listingquality.enrichment.model.book.BookEnrichmentRequest;
import dev.iseif.listingquality.enrichment.model.book.BookEnrichmentResponse;
import dev.iseif.listingquality.enrichment.observability.EnrichmentTelemetry;
import dev.iseif.listingquality.enrichment.prompt.BookEnrichmentPrompt;
import org.springframework.stereotype.Service;

@Service
public final class BookEnrichmentService {

  private final BookCatalogClient catalogClient;
  private final BookEnrichmentPrompt prompt;
  private final FailoverBookEnrichmentGenerator failover;
  private final EnrichmentTelemetry telemetry;

  public BookEnrichmentService(
      BookCatalogClient catalogClient,
      BookEnrichmentPrompt prompt,
      FailoverBookEnrichmentGenerator failover,
      EnrichmentTelemetry telemetry) {
    this.catalogClient = catalogClient;
    this.prompt = prompt;
    this.failover = failover;
    this.telemetry = telemetry;
  }

  public BookEnrichmentResponse enrich(BookEnrichmentRequest request) {
    return telemetry.observeBook(() -> {
      BookCatalogEvidenceLedger ledger = new BookCatalogEvidenceLedger();
      BookCatalogTools tools = new BookCatalogTools(catalogClient, ledger);
      String renderedPrompt = prompt.render(request);
      BookEnrichmentExecution execution = failover.execute(request, renderedPrompt, tools, ledger);
      return execution.toResponse(ledger.warnings());
    });
  }
}
