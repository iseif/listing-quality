package dev.iseif.listingquality.enrichment.config;

import dev.iseif.listingquality.enrichment.catalog.book.BookCatalogClient;
import dev.iseif.listingquality.enrichment.catalog.book.google.GoogleBooksCatalogClient;
import dev.iseif.listingquality.enrichment.catalog.book.google.GoogleBooksProperties;
import dev.iseif.listingquality.enrichment.observability.EnrichmentTelemetry;
import dev.iseif.listingquality.enrichment.service.book.*;
import dev.iseif.listingquality.enrichment.service.execution.EnrichmentFailureClassifier;
import dev.iseif.listingquality.enrichment.service.execution.ModelRoute;
import dev.iseif.listingquality.enrichment.service.execution.ModelRouteFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "google-genai")
@EnableConfigurationProperties(GoogleBooksProperties.class)
class BookEnrichmentConfiguration {

  @Bean
  BookCatalogClient bookCatalogClient(
      RestClient.Builder restClientBuilder,
      ObjectMapper objectMapper,
      GoogleBooksProperties properties) {
    return new GoogleBooksCatalogClient(restClientBuilder, objectMapper, properties);
  }

  @Bean("geminiBookChatClient")
  ChatClient geminiBookChatClient(
      EnrichmentChatModels models,
      EnrichmentChatClientFactory factory,
      @Value("classpath:/prompts/book-enrichment-system.st") Resource systemPrompt)
      throws IOException {
    return factory.create(models.gemini(), read(systemPrompt));
  }

  @Bean("omlxBookChatClient")
  ChatClient omlxBookChatClient(
      EnrichmentChatModels models,
      EnrichmentChatClientFactory factory,
      @Value("classpath:/prompts/book-enrichment-system.st") Resource systemPrompt)
      throws IOException {
    return factory.create(models.omlx(), read(systemPrompt));
  }

  @Bean("geminiBookGenerator")
  BookEnrichmentGenerator geminiBookGenerator(
      @Qualifier("geminiBookChatClient") ChatClient chatClient,
      EnrichmentProperties properties) {
    return new SpringAiBookEnrichmentGenerator(
        "gemini", chatClient, properties.resilience().structuredOutputAttempts());
  }

  @Bean("omlxBookGenerator")
  BookEnrichmentGenerator omlxBookGenerator(
      @Qualifier("omlxBookChatClient") ChatClient chatClient,
      EnrichmentProperties properties) {
    return new SpringAiBookEnrichmentGenerator(
        "omlx", chatClient, properties.resilience().structuredOutputAttempts());
  }

  @Bean
  BookEnrichmentGeneratorRegistry bookEnrichmentGeneratorRegistry(
      @Qualifier("geminiBookGenerator") BookEnrichmentGenerator gemini,
      @Qualifier("omlxBookGenerator") BookEnrichmentGenerator omlx) {
    return new BookEnrichmentGeneratorRegistry(Map.of("gemini", gemini, "omlx", omlx));
  }

  @Bean("bookPrimaryModelRoute")
  ModelRoute bookPrimaryModelRoute(
      EnrichmentProperties properties,
      ModelRouteFactory factory) {
    String provider = properties.books().primary();
    return factory.create(
        "book-" + provider + "-primary",
        provider,
        properties.resilience().primaryTimeout());
  }

  @Bean("bookFallbackModelRoute")
  ModelRoute bookFallbackModelRoute(
      EnrichmentProperties properties,
      ModelRouteFactory factory) {
    String provider = properties.books().fallback();
    return factory.create(
        "book-" + provider + "-fallback",
        provider,
        properties.resilience().fallbackTimeout());
  }

  @Bean
  FailoverBookEnrichmentGenerator failoverBookEnrichmentGenerator(
      BookEnrichmentGeneratorRegistry generators,
      @Qualifier("bookPrimaryModelRoute") ModelRoute primaryRoute,
      @Qualifier("bookFallbackModelRoute") ModelRoute fallbackRoute,
      BookEnrichmentValidator validator,
      EnrichmentFailureClassifier classifier,
      EnrichmentProperties properties,
      EnrichmentTelemetry telemetry) {
    return new FailoverBookEnrichmentGenerator(
        generators.require(properties.books().primary()),
        generators.require(properties.books().fallback()),
        primaryRoute,
        fallbackRoute,
        validator,
        classifier,
        properties.resilience().overallTimeout(),
        telemetry);
  }

  private String read(Resource resource) throws IOException {
    return resource.getContentAsString(StandardCharsets.UTF_8);
  }
}
