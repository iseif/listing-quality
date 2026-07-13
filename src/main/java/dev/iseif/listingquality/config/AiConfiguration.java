package dev.iseif.listingquality.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ListingReviewAiProperties.class)
public class AiConfiguration {

  @Bean
  ChatClient listingReviewChatClient(
      ChatClient.Builder chatClientBuilder,
      @Value("classpath:/prompts/listing-review-system.st") Resource systemPrompt,
      ListingReviewAiProperties properties) {
    return chatClientBuilder
        .defaultSystem(systemPrompt)
        .defaultOptions(ChatOptions.builder()
            .temperature(properties.temperature()))
        .build();
  }
}
