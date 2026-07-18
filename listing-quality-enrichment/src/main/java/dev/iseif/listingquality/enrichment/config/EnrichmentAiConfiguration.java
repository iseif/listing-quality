package dev.iseif.listingquality.enrichment.config;

import dev.iseif.listingquality.enrichment.catalog.book.BookCatalogClient;
import dev.iseif.listingquality.enrichment.catalog.book.google.GoogleBooksCatalogClient;
import dev.iseif.listingquality.enrichment.catalog.book.google.GoogleBooksProperties;
import dev.iseif.listingquality.enrichment.service.book.*;
import dev.iseif.listingquality.enrichment.service.execution.EnrichmentFailureClassifier;
import dev.iseif.listingquality.enrichment.service.execution.ModelRoute;
import dev.iseif.listingquality.enrichment.service.execution.RoutePolicy;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.observation.AdvisorObservationConvention;
import org.springframework.ai.chat.client.observation.ChatClientObservationConvention;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.chat.client.autoconfigure.ChatClientBuilderConfigurer;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
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
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "google-genai")
@EnableConfigurationProperties({EnrichmentProperties.class, GoogleBooksProperties.class})
public class EnrichmentAiConfiguration {

  private static final String GEMINI = "gemini";
  private static final String OMLX = "omlx";

  @Bean
  BookCatalogClient bookCatalogClient(
      RestClient.Builder restClientBuilder,
      ObjectMapper objectMapper,
      GoogleBooksProperties properties) {
    return new GoogleBooksCatalogClient(restClientBuilder, objectMapper, properties);
  }

  @Bean(destroyMethod = "close")
  ExecutorService enrichmentExecutor() {
    return Executors.newVirtualThreadPerTaskExecutor();
  }

  @Bean
  EnrichmentFailureClassifier enrichmentFailureClassifier() {
    return new EnrichmentFailureClassifier();
  }

  @Bean("geminiChatClient")
  ChatClient geminiChatClient(
      ChatModel chatModel,
      ChatClientBuilderConfigurer configurer,
      ObjectProvider<ObservationRegistry> observationRegistry,
      ObjectProvider<ChatClientObservationConvention> chatObservationConvention,
      ObjectProvider<AdvisorObservationConvention> advisorObservationConvention,
      ObjectProvider<ToolCallingAdvisor.Builder<?>> toolCallingAdvisorBuilder,
      @Value("classpath:/prompts/book-enrichment-system.st") Resource systemPrompt)
      throws IOException {
    return chatClientFactory(
        configurer,
        observationRegistry,
        chatObservationConvention,
        advisorObservationConvention,
        toolCallingAdvisorBuilder,
        systemPrompt)
        .create(chatModel);
  }

  /**
   * oMLX speaks the OpenAI protocol, so its model is built here rather than published as a second
   * {@link ChatModel} bean, which would make the primary {@link ChatModel} injection ambiguous.
   */
  @Bean("omlxChatClient")
  ChatClient omlxChatClient(
      EnrichmentProperties properties,
      ChatClientBuilderConfigurer configurer,
      ObjectProvider<ObservationRegistry> observationRegistry,
      ObjectProvider<ChatClientObservationConvention> chatObservationConvention,
      ObjectProvider<AdvisorObservationConvention> advisorObservationConvention,
      ObjectProvider<ToolCallingAdvisor.Builder<?>> toolCallingAdvisorBuilder,
      @Value("classpath:/prompts/book-enrichment-system.st") Resource systemPrompt)
      throws IOException {
    EnrichmentChatClientFactory factory = chatClientFactory(
        configurer,
        observationRegistry,
        chatObservationConvention,
        advisorObservationConvention,
        toolCallingAdvisorBuilder,
        systemPrompt);
    EnrichmentProperties.Provider omlx = properties.provider(OMLX);
    OpenAiChatOptions options = OpenAiChatOptions.builder()
        .baseUrl(omlx.baseUrl().toString())
        .apiKey(omlx.apiKey())
        .model(omlx.model())
        .maxRetries(0)
        .build();
    return factory.create(OpenAiChatModel.builder()
        .options(options)
        .observationRegistry(factory.observationRegistry())
        .build());
  }

  @Bean("geminiGenerator")
  BookEnrichmentGenerator geminiGenerator(
      @Qualifier("geminiChatClient") ChatClient chatClient,
      EnrichmentProperties properties) {
    return new SpringAiBookEnrichmentGenerator(
        GEMINI, chatClient, properties.resilience().structuredOutputAttempts());
  }

  @Bean("omlxGenerator")
  BookEnrichmentGenerator omlxGenerator(
      @Qualifier("omlxChatClient") ChatClient chatClient,
      EnrichmentProperties properties) {
    return new SpringAiBookEnrichmentGenerator(
        OMLX, chatClient, properties.resilience().structuredOutputAttempts());
  }

  @Bean
  BookEnrichmentGeneratorRegistry bookEnrichmentGeneratorRegistry(
      @Qualifier("geminiGenerator") BookEnrichmentGenerator geminiGenerator,
      @Qualifier("omlxGenerator") BookEnrichmentGenerator omlxGenerator) {
    return new BookEnrichmentGeneratorRegistry(
        Map.of(GEMINI, geminiGenerator, OMLX, omlxGenerator));
  }

  @Bean("primaryModelRoute")
  ModelRoute primaryModelRoute(
      EnrichmentProperties properties,
      EnrichmentFailureClassifier classifier,
      ExecutorService enrichmentExecutor) {
    return modelRoute(
        properties.primary(),
        properties.resilience().primaryTimeout(),
        properties,
        classifier,
        enrichmentExecutor);
  }

  @Bean("fallbackModelRoute")
  ModelRoute fallbackModelRoute(
      EnrichmentProperties properties,
      EnrichmentFailureClassifier classifier,
      ExecutorService enrichmentExecutor) {
    return modelRoute(
        properties.fallback(),
        properties.resilience().fallbackTimeout(),
        properties,
        classifier,
        enrichmentExecutor);
  }

  @Bean
  FailoverBookEnrichmentGenerator failoverEnrichmentGenerator(
      BookEnrichmentGeneratorRegistry generators,
      @Qualifier("primaryModelRoute") ModelRoute primaryRoute,
      @Qualifier("fallbackModelRoute") ModelRoute fallbackRoute,
      BookEnrichmentValidator validator,
      EnrichmentFailureClassifier classifier,
      EnrichmentProperties properties) {
    return new FailoverBookEnrichmentGenerator(
        generators.require(properties.primary()),
        generators.require(properties.fallback()),
        primaryRoute,
        fallbackRoute,
        validator,
        classifier,
        properties.resilience().overallTimeout());
  }

  private EnrichmentChatClientFactory chatClientFactory(
      ChatClientBuilderConfigurer configurer,
      ObjectProvider<ObservationRegistry> observationRegistry,
      ObjectProvider<ChatClientObservationConvention> chatObservationConvention,
      ObjectProvider<AdvisorObservationConvention> advisorObservationConvention,
      ObjectProvider<ToolCallingAdvisor.Builder<?>> toolCallingAdvisorBuilder,
      Resource systemPrompt) throws IOException {
    return new EnrichmentChatClientFactory(
        configurer,
        observationRegistry,
        chatObservationConvention,
        advisorObservationConvention,
        toolCallingAdvisorBuilder,
        systemPrompt.getContentAsString(StandardCharsets.UTF_8));
  }

  private ModelRoute modelRoute(
      String providerId,
      Duration timeout,
      EnrichmentProperties properties,
      EnrichmentFailureClassifier classifier,
      ExecutorService executor) {
    EnrichmentProperties.Resilience resilience = properties.resilience();
    RoutePolicy policy = new RoutePolicy(
        resilience.maxAttempts(),
        resilience.retryWait(),
        resilience.circuitWindow(),
        resilience.circuitFailureThreshold(),
        resilience.circuitOpenDuration(),
        resilience.halfOpenCalls());
    return new ModelRoute(providerId, timeout, policy, classifier, executor);
  }
}
