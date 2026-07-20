package dev.iseif.listingquality.enrichment.config;

import dev.iseif.listingquality.enrichment.service.execution.EnrichmentFailureClassifier;
import dev.iseif.listingquality.enrichment.service.execution.ModelRouteFactory;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.observation.AdvisorObservationConvention;
import org.springframework.ai.chat.client.observation.ChatClientObservationConvention;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.chat.client.autoconfigure.ChatClientBuilderConfigurer;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "google-genai")
@EnableConfigurationProperties(EnrichmentProperties.class)
class EnrichmentModelConfiguration {

  @Bean(destroyMethod = "close")
  ExecutorService enrichmentExecutor() {
    return Executors.newVirtualThreadPerTaskExecutor();
  }

  @Bean
  EnrichmentFailureClassifier enrichmentFailureClassifier() {
    return new EnrichmentFailureClassifier();
  }

  @Bean
  ModelRouteFactory modelRouteFactory(
      EnrichmentProperties properties,
      EnrichmentFailureClassifier classifier,
      ExecutorService enrichmentExecutor) {
    return new ModelRouteFactory(properties.resilience(), classifier, enrichmentExecutor);
  }

  @Bean
  EnrichmentChatClientFactory enrichmentChatClientFactory(
      ChatClientBuilderConfigurer configurer,
      ObjectProvider<ObservationRegistry> observationRegistry,
      ObjectProvider<ChatClientObservationConvention> chatObservationConvention,
      ObjectProvider<AdvisorObservationConvention> advisorObservationConvention,
      ObjectProvider<ToolCallingAdvisor.Builder<?>> toolCallingAdvisorBuilder) {
    return new EnrichmentChatClientFactory(
        configurer,
        observationRegistry,
        chatObservationConvention,
        advisorObservationConvention,
        toolCallingAdvisorBuilder);
  }

  @Bean
  EnrichmentChatModels enrichmentChatModels(
      ChatModel geminiChatModel,
      EnrichmentProperties properties,
      EnrichmentChatClientFactory chatClientFactory) {
    EnrichmentProperties.Provider omlx = properties.provider("omlx");
    OpenAiChatOptions options = OpenAiChatOptions.builder()
        .baseUrl(omlx.baseUrl().toString())
        .apiKey(omlx.apiKey())
        .model(omlx.model())
        .maxRetries(0)
        .build();
    ChatModel omlxChatModel = OpenAiChatModel.builder()
        .options(options)
        .observationRegistry(chatClientFactory.observationRegistry())
        .build();
    return new EnrichmentChatModels(geminiChatModel, omlxChatModel);
  }
}
