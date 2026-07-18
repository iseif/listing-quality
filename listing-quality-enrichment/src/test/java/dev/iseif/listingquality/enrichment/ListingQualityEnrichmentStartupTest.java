package dev.iseif.listingquality.enrichment;

import dev.iseif.listingquality.enrichment.catalog.book.BookCatalogClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    classes = ListingQualityEnrichmentApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.ai.google.genai.api-key=test-gemini-key",
        "listing-quality.enrichment.catalog.google-books.api-key=test-google-books-key"
    })
class ListingQualityEnrichmentStartupTest {

  @Autowired
  private BookCatalogClient catalogClient;

  @Test
  void startsWithTheRealGeminiAutoConfiguration() {
    assertThat(catalogClient).isNotNull();
  }
}
