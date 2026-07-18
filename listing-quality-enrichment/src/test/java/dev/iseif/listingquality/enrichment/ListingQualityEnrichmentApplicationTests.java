package dev.iseif.listingquality.enrichment;

import dev.iseif.listingquality.enrichment.catalog.book.BookCatalogClient;
import dev.iseif.listingquality.enrichment.service.book.FailoverBookEnrichmentGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
    classes = ListingQualityEnrichmentApplication.class,
    properties = "spring.ai.model.chat=none")
class ListingQualityEnrichmentApplicationTests {

  @MockitoBean
  private BookCatalogClient catalogClient;

  @MockitoBean
  private FailoverBookEnrichmentGenerator failover;

  @Test
  void contextLoads() {
  }
}
