package dev.iseif.listingquality.enrichment.config;

import dev.iseif.listingquality.enrichment.media.*;
import dev.iseif.listingquality.enrichment.observability.EnrichmentTelemetry;
import dev.iseif.listingquality.enrichment.prompt.ShoeColorPrompt;
import dev.iseif.listingquality.enrichment.service.execution.EnrichmentFailureClassifier;
import dev.iseif.listingquality.enrichment.service.execution.ModelRoute;
import dev.iseif.listingquality.enrichment.service.execution.ModelRouteFactory;
import dev.iseif.listingquality.enrichment.service.shoe.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "google-genai")
@EnableConfigurationProperties(ImageLoadingProperties.class)
class ShoeColorEnrichmentConfiguration {

  @Bean("enrichmentImageHttpClient")
  HttpClient enrichmentImageHttpClient(ImageLoadingProperties properties) {
    return HttpClient.newBuilder()
        .connectTimeout(properties.connectTimeout())
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();
  }

  @Bean
  ImageUriValidator imageUriValidator(ImageLoadingProperties properties) {
    return new TrustedImageUriValidator(properties);
  }

  @Bean
  ProductImageLoader productImageLoader(
      @Qualifier("enrichmentImageHttpClient") HttpClient httpClient,
      ImageUriValidator uriValidator,
      ImageLoadingProperties properties) {
    return new SafeHttpProductImageLoader(httpClient, uriValidator, properties);
  }

  @Bean("geminiShoeColorChatClient")
  ChatClient geminiShoeColorChatClient(
      EnrichmentChatModels models,
      EnrichmentChatClientFactory factory,
      @Value("classpath:/prompts/shoe-color-extraction-system.st") Resource systemPrompt)
      throws IOException {
    return factory.create(models.gemini(), read(systemPrompt));
  }

  @Bean("omlxShoeColorChatClient")
  ChatClient omlxShoeColorChatClient(
      EnrichmentChatModels models,
      EnrichmentChatClientFactory factory,
      @Value("classpath:/prompts/shoe-color-extraction-system.st") Resource systemPrompt)
      throws IOException {
    return factory.create(models.omlx(), read(systemPrompt));
  }

  @Bean("geminiShoeColorGenerator")
  ShoeColorGenerator geminiShoeColorGenerator(
      @Qualifier("geminiShoeColorChatClient") ChatClient chatClient,
      EnrichmentProperties properties) {
    return new SpringAiShoeColorGenerator(
        "gemini", chatClient, properties.resilience().structuredOutputAttempts());
  }

  @Bean("omlxShoeColorGenerator")
  ShoeColorGenerator omlxShoeColorGenerator(
      @Qualifier("omlxShoeColorChatClient") ChatClient chatClient,
      EnrichmentProperties properties) {
    return new SpringAiShoeColorGenerator(
        "omlx", chatClient, properties.resilience().structuredOutputAttempts());
  }

  @Bean
  ShoeColorGeneratorRegistry shoeColorGeneratorRegistry(
      @Qualifier("geminiShoeColorGenerator") ShoeColorGenerator gemini,
      @Qualifier("omlxShoeColorGenerator") ShoeColorGenerator omlx) {
    return new ShoeColorGeneratorRegistry(Map.of("gemini", gemini, "omlx", omlx));
  }

  @Bean
  ShoeColorExtractionValidator shoeColorExtractionValidator() {
    return new ShoeColorExtractionValidator();
  }

  @Bean
  ShoeColorComparisonPolicy shoeColorComparisonPolicy() {
    return new ShoeColorComparisonPolicy();
  }

  @Bean("shoeColorPrimaryModelRoute")
  ModelRoute shoeColorPrimaryModelRoute(
      EnrichmentProperties properties,
      ModelRouteFactory factory) {
    String provider = properties.shoeColors().primary();
    return factory.create(
        "shoe-color-" + provider + "-primary",
        provider,
        properties.resilience().primaryTimeout());
  }

  @Bean("shoeColorFallbackModelRoute")
  ModelRoute shoeColorFallbackModelRoute(
      EnrichmentProperties properties,
      ModelRouteFactory factory) {
    String provider = properties.shoeColors().fallback();
    return factory.create(
        "shoe-color-" + provider + "-fallback",
        provider,
        properties.resilience().fallbackTimeout());
  }

  @Bean
  FailoverShoeColorGenerator failoverShoeColorGenerator(
      ShoeColorGeneratorRegistry generators,
      @Qualifier("shoeColorPrimaryModelRoute") ModelRoute primaryRoute,
      @Qualifier("shoeColorFallbackModelRoute") ModelRoute fallbackRoute,
      ShoeColorExtractionValidator validator,
      EnrichmentFailureClassifier classifier,
      EnrichmentProperties properties,
      EnrichmentTelemetry telemetry) {
    return new FailoverShoeColorGenerator(
        generators.require(properties.shoeColors().primary()),
        generators.require(properties.shoeColors().fallback()),
        primaryRoute,
        fallbackRoute,
        validator,
        classifier,
        properties.resilience().overallTimeout(),
        telemetry);
  }

  @Bean
  ShoeColorEnrichmentService shoeColorEnrichmentService(
      ProductImageLoader imageLoader,
      ShoeColorPrompt prompt,
      FailoverShoeColorGenerator failover,
      ShoeColorComparisonPolicy comparisonPolicy,
      EnrichmentTelemetry telemetry) {
    return new ShoeColorEnrichmentService(
        imageLoader, prompt, failover, comparisonPolicy, telemetry);
  }

  private String read(Resource resource) throws IOException {
    return resource.getContentAsString(StandardCharsets.UTF_8);
  }
}
