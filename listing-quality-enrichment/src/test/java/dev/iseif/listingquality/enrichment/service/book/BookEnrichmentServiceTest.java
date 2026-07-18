package dev.iseif.listingquality.enrichment.service.book;

import dev.iseif.listingquality.enrichment.catalog.book.BookCatalogClient;
import dev.iseif.listingquality.enrichment.catalog.book.BookCatalogEvidenceLedger;
import dev.iseif.listingquality.enrichment.catalog.book.BookCatalogTools;
import dev.iseif.listingquality.enrichment.model.ExecutionRoute;
import dev.iseif.listingquality.enrichment.model.book.BookEnrichmentRequest;
import dev.iseif.listingquality.enrichment.model.book.BookEnrichmentResponse;
import dev.iseif.listingquality.enrichment.model.book.EnrichmentStatus;
import dev.iseif.listingquality.enrichment.prompt.BookEnrichmentPrompt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BookEnrichmentServiceTest {

  @Mock
  private BookCatalogClient catalogClient;

  @Mock
  private BookEnrichmentPrompt prompt;

  @Mock
  private FailoverBookEnrichmentGenerator failover;

  @Test
  void createsOneRequestScopedLedgerAndToolSet() {
    BookEnrichmentRequest request = new BookEnrichmentRequest(
        "book-1", "Clean Code", null, null, Map.of());
    ValidatedBookEnrichment validated = new ValidatedBookEnrichment(
        EnrichmentStatus.NO_SAFE_PROPOSALS, List.of(), List.of(), List.of(), List.of());
    given(prompt.render(request)).willReturn("rendered");
    given(failover.execute(eq(request), eq("rendered"), any(), any()))
        .willReturn(new BookEnrichmentExecution(validated, ExecutionRoute.PRIMARY));

    BookEnrichmentResponse response = new BookEnrichmentService(
        catalogClient, prompt, failover).enrich(request);

    assertThat(response.execution().route()).isEqualTo(ExecutionRoute.PRIMARY);
    assertThat(response.requiresSellerApproval()).isFalse();
    ArgumentCaptor<BookCatalogTools> tools = ArgumentCaptor.forClass(BookCatalogTools.class);
    ArgumentCaptor<BookCatalogEvidenceLedger> ledger =
        ArgumentCaptor.forClass(BookCatalogEvidenceLedger.class);
    verify(failover).execute(eq(request), eq("rendered"), tools.capture(), ledger.capture());
    assertThat(tools.getValue()).isNotNull();
    assertThat(ledger.getValue()).isNotNull();
  }
}
