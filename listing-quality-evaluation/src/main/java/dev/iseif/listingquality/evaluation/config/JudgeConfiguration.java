package dev.iseif.listingquality.evaluation.config;

import dev.iseif.listingquality.evaluation.grade.SemanticListingEvaluator;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;

@Configuration(proxyBeanMethods = false)
@Profile("judge-openai")
public class JudgeConfiguration {

  @Bean
  @Qualifier("evaluationJudgeChatClient")
  ChatClient evaluationJudgeChatClient(
      ChatClient.Builder builder,
      @Value("classpath:/prompts/listing-review-judge-system.st") Resource systemPrompt) {
    return builder.defaultSystem(systemPrompt).build();
  }

  @Bean
  SemanticListingEvaluator semanticListingEvaluator(
      @Qualifier("evaluationJudgeChatClient") ChatClient chatClient,
      @Value("classpath:/prompts/listing-review-judge.st") Resource userPrompt) {
    return new SemanticListingEvaluator(chatClient, userPrompt);
  }
}
