package dev.iseif.listingquality.enrichment.config;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.observation.AdvisorObservationConvention;
import org.springframework.ai.chat.client.observation.ChatClientObservationConvention;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.chat.client.autoconfigure.ChatClientBuilderConfigurer;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Builds a {@link ChatClient} for one provider while keeping the Spring AI customizers, advisors,
 * tool execution, and observations that the auto-configured builder would have applied.
 *
 * <p>Both routes need an identically configured client over a different {@link ChatModel}, so the
 * auto-configuration plumbing is resolved once here instead of in every bean method.
 */
final class EnrichmentChatClientFactory {

  private final ChatClientBuilderConfigurer configurer;
  private final ObservationRegistry observationRegistry;
  private final ObjectProvider<ChatClientObservationConvention> chatObservationConvention;
  private final ObjectProvider<AdvisorObservationConvention> advisorObservationConvention;
  private final ObjectProvider<ToolCallingAdvisor.Builder<?>> toolCallingAdvisorBuilder;

  EnrichmentChatClientFactory(
      ChatClientBuilderConfigurer configurer,
      ObjectProvider<ObservationRegistry> observationRegistry,
      ObjectProvider<ChatClientObservationConvention> chatObservationConvention,
      ObjectProvider<AdvisorObservationConvention> advisorObservationConvention,
      ObjectProvider<ToolCallingAdvisor.Builder<?>> toolCallingAdvisorBuilder) {
    this.configurer = configurer;
    this.observationRegistry = observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP);
    this.chatObservationConvention = chatObservationConvention;
    this.advisorObservationConvention = advisorObservationConvention;
    this.toolCallingAdvisorBuilder = toolCallingAdvisorBuilder;
  }

  ObservationRegistry observationRegistry() {
    return observationRegistry;
  }

  ChatClient create(ChatModel chatModel, String systemPrompt) {
    ChatClient.Builder builder = ChatClient.builder(
        chatModel,
        observationRegistry,
        chatObservationConvention.getIfUnique(),
        advisorObservationConvention.getIfUnique(),
        toolCallingAdvisorBuilder.getIfAvailable());
    return configurer.configure(builder)
        .defaultSystem(systemPrompt)
        .build();
  }
}
