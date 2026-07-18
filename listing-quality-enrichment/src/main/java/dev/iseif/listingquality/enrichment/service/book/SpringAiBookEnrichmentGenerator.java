package dev.iseif.listingquality.enrichment.service.book;

import dev.iseif.listingquality.enrichment.catalog.book.BookCatalogTools;
import dev.iseif.listingquality.enrichment.model.book.GeneratedBookEnrichment;
import dev.iseif.listingquality.enrichment.service.execution.ModelExecutionException;
import dev.iseif.listingquality.enrichment.service.execution.ModelFailureCategory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.StructuredOutputValidationAdvisor;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;

import java.util.Objects;

public final class SpringAiBookEnrichmentGenerator implements BookEnrichmentGenerator {

  private final String providerId;
  private final ChatClient chatClient;
  private final StructuredOutputValidationAdvisor validationAdvisor;

  public SpringAiBookEnrichmentGenerator(
      String providerId,
      ChatClient chatClient,
      int structuredOutputAttempts) {
    this.providerId = Objects.requireNonNull(providerId);
    this.chatClient = Objects.requireNonNull(chatClient);
    if (structuredOutputAttempts < 1) {
      throw new IllegalArgumentException("structuredOutputAttempts must be at least one");
    }
    this.validationAdvisor = StructuredOutputValidationAdvisor.builder()
        .outputType(GeneratedBookEnrichment.class)
        .maxRepeatAttempts(structuredOutputAttempts - 1)
        .build();
  }

  @Override
  public GeneratedBookEnrichment generate(String prompt, BookCatalogTools tools) {
    try {
      return chatClient.prompt()
          .user(prompt)
          .tools(tools)
          .advisors(validationAdvisor)
          .call()
          .entity(GeneratedBookEnrichment.class);
    } catch (TransientAiException exception) {
      throw ModelExecutionException.eligible(
          providerId, ModelFailureCategory.PROVIDER_UNAVAILABLE, exception);
    } catch (NonTransientAiException exception) {
      throw ModelExecutionException.ineligible(
          providerId, ModelFailureCategory.CONFIGURATION_ERROR, exception);
    } catch (RuntimeException exception) {
      throw ModelExecutionException.eligible(
          providerId, ModelFailureCategory.INVALID_MODEL_OUTPUT, exception);
    }
  }
}
