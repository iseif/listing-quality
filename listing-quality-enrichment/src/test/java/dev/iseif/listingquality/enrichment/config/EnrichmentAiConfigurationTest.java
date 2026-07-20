package dev.iseif.listingquality.enrichment.config;

import dev.iseif.listingquality.enrichment.ListingQualityEnrichmentApplication;
import dev.iseif.listingquality.enrichment.service.book.BookEnrichmentGenerator;
import dev.iseif.listingquality.enrichment.service.book.SpringAiBookEnrichmentGenerator;
import dev.iseif.listingquality.enrichment.service.execution.ModelRoute;
import dev.iseif.listingquality.enrichment.service.shoe.ShoeColorGenerator;
import dev.iseif.listingquality.enrichment.service.shoe.SpringAiShoeColorGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
@SpringBootTest(
    classes = ListingQualityEnrichmentApplication.class,
    properties = {
        "spring.ai.model.chat=google-genai",
        "spring.ai.google.genai.api-key=test-gemini-key",
        "listing-quality.enrichment.books.primary=gemini",
        "listing-quality.enrichment.books.fallback=omlx",
        "listing-quality.enrichment.shoe-colors.primary=omlx",
        "listing-quality.enrichment.shoe-colors.fallback=gemini",
        "listing-quality.enrichment.providers.gemini.model=gemini-3.5-flash",
        "listing-quality.enrichment.providers.gemini.api-key=test-gemini-key",
        "listing-quality.enrichment.providers.omlx.model=gemma-4-26B-A4B-it-MLX-8bit",
        "listing-quality.enrichment.providers.omlx.base-url=http://localhost:8000/v1",
        "listing-quality.enrichment.providers.omlx.api-key=local",
        "listing-quality.enrichment.catalog.google-books.base-url=https://www.googleapis.com/books/v1",
        "listing-quality.enrichment.catalog.google-books.api-key=test-google-books-key",
        "listing-quality.enrichment.catalog.google-books.timeout=2s",
        "listing-quality.enrichment.catalog.google-books.max-results=3",
        "listing-quality.enrichment.catalog.google-books.max-response-bytes=262144",
        "listing-quality.enrichment.resilience.max-attempts=2",
        "listing-quality.enrichment.resilience.structured-output-attempts=2",
        "listing-quality.enrichment.resilience.primary-timeout=10s",
        "listing-quality.enrichment.resilience.fallback-timeout=20s",
        "listing-quality.enrichment.resilience.overall-timeout=25s",
        "listing-quality.enrichment.resilience.circuit-window=10",
        "listing-quality.enrichment.resilience.circuit-failure-threshold=50",
        "listing-quality.enrichment.resilience.circuit-open-duration=30s",
        "listing-quality.enrichment.resilience.half-open-calls=2"
    })
class EnrichmentAiConfigurationTest {

  @Autowired
  private ApplicationContext context;

  @Autowired
  private EnrichmentProperties properties;

  @Autowired
  private GoogleGenAiChatModel geminiChatModel;

  @Test
  void createsTwoNamedBookGeneratorsWithoutPublishingASecondChatModel() {
    BookEnrichmentGenerator gemini = context.getBean(
        "geminiBookGenerator", BookEnrichmentGenerator.class);
    BookEnrichmentGenerator omlx = context.getBean(
        "omlxBookGenerator", BookEnrichmentGenerator.class);

    assertThat(gemini).isInstanceOf(SpringAiBookEnrichmentGenerator.class);
    assertThat(omlx).isInstanceOf(SpringAiBookEnrichmentGenerator.class);
    assertThat(gemini).isNotSameAs(omlx);
    assertThat(context.getBeansOfType(ChatModel.class)).hasSize(1);
  }

  @Test
  void createsCategorySpecificChatClientsAndShoeGenerators() {
    assertThat(context.getBeansOfType(org.springframework.ai.chat.client.ChatClient.class))
        .containsKeys(
            "geminiBookChatClient",
            "omlxBookChatClient",
            "geminiShoeColorChatClient",
            "omlxShoeColorChatClient");
    assertThat(context.getBean("geminiShoeColorGenerator", ShoeColorGenerator.class))
        .isInstanceOf(SpringAiShoeColorGenerator.class);
    assertThat(context.getBean("omlxShoeColorGenerator", ShoeColorGenerator.class))
        .isInstanceOf(SpringAiShoeColorGenerator.class);
  }

  @Test
  void createsIndependentBookAndShoeModelRoutes() {
    ModelRoute bookPrimary = context.getBean("bookPrimaryModelRoute", ModelRoute.class);
    ModelRoute shoePrimary = context.getBean("shoeColorPrimaryModelRoute", ModelRoute.class);

    assertThat(bookPrimary).isNotSameAs(shoePrimary);
    assertThat(context.getBeansOfType(ModelRoute.class)).hasSize(4);
  }

  @Test
  void bindsIndependentBookAndShoeRoutes() {
    assertThat(properties.books().primary()).isEqualTo("gemini");
    assertThat(properties.books().fallback()).isEqualTo("omlx");
    assertThat(properties.shoeColors().primary()).isEqualTo("omlx");
    assertThat(properties.shoeColors().fallback()).isEqualTo("gemini");
  }

  @Test
  void keepsOmlxInsideTheProviderHolder() {
    EnrichmentChatModels models = context.getBean(EnrichmentChatModels.class);

    assertThat(models.gemini()).isSameAs(geminiChatModel);
    assertThat(models.omlx()).isNotSameAs(geminiChatModel);
    assertThat(context.getBeansOfType(ChatModel.class)).hasSize(1);
  }

  @Test
  void bindsTheLocalEndpointAndModel() {
    assertThat(properties.provider("omlx").baseUrl())
        .isEqualTo(URI.create("http://localhost:8000/v1"));
    assertThat(properties.provider("omlx").model())
        .isEqualTo("gemma-4-26B-A4B-it-MLX-8bit");
  }

  @Test
  void reservesEnoughGeminiOutputTokensForTheEnrichmentContract() {
    assertThat(geminiChatModel.getOptions().getMaxOutputTokens()).isEqualTo(8192);
  }
}
