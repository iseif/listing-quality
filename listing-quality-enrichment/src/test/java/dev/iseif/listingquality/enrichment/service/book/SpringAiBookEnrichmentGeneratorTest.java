package dev.iseif.listingquality.enrichment.service.book;

import dev.iseif.listingquality.enrichment.catalog.book.BookCatalogTools;
import dev.iseif.listingquality.enrichment.model.book.GeneratedBookEnrichment;
import dev.iseif.listingquality.enrichment.service.execution.ModelExecutionException;
import dev.iseif.listingquality.enrichment.service.execution.ModelFailureCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SpringAiBookEnrichmentGeneratorTest {

  @Mock
  private ChatClient chatClient;

  @Mock
  private ChatClientRequestSpec requestSpec;

  @Mock
  private CallResponseSpec responseSpec;

  @Mock
  private BookCatalogTools tools;

  private SpringAiBookEnrichmentGenerator generator;

  @BeforeEach
  void setUp() {
    given(chatClient.prompt()).willReturn(requestSpec);
    given(requestSpec.user("rendered prompt")).willReturn(requestSpec);
    given(requestSpec.tools(tools)).willReturn(requestSpec);
    given(requestSpec.advisors(any(Advisor.class))).willReturn(requestSpec);
    given(requestSpec.call()).willReturn(responseSpec);
    generator = new SpringAiBookEnrichmentGenerator("gemini", chatClient, 2);
  }

  @Test
  void returnsStructuredOutputAndRegistersTheRequestScopedTools() {
    GeneratedBookEnrichment expected = new GeneratedBookEnrichment(
        List.of(), List.of(), List.of(), List.of());
    given(responseSpec.entity(GeneratedBookEnrichment.class)).willReturn(expected);

    assertThat(generator.generate("rendered prompt", tools)).isSameAs(expected);
    verify(requestSpec).tools(tools);
  }

  @Test
  void classifiesExhaustedStructuredOutputAsEligibleForFallback() {
    given(responseSpec.entity(GeneratedBookEnrichment.class))
        .willThrow(new RuntimeException("malformed response"));

    assertThatThrownBy(() -> generator.generate("rendered prompt", tools))
        .isInstanceOfSatisfying(ModelExecutionException.class, failure -> {
          assertThat(failure.providerId()).isEqualTo("gemini");
          assertThat(failure.category()).isEqualTo(ModelFailureCategory.INVALID_MODEL_OUTPUT);
          assertThat(failure.eligible()).isTrue();
          assertThat(failure.getMessage()).isEqualTo("AI model execution failed");
        });
  }

  @Test
  void classifiesTransientFailureAsEligibleWithoutSchemaRetry() {
    given(responseSpec.entity(GeneratedBookEnrichment.class))
        .willThrow(new TransientAiException("quota detail"));

    assertThatThrownBy(() -> generator.generate("rendered prompt", tools))
        .isInstanceOfSatisfying(ModelExecutionException.class, failure -> {
          assertThat(failure.category()).isEqualTo(ModelFailureCategory.PROVIDER_UNAVAILABLE);
          assertThat(failure.eligible()).isTrue();
        });
    verify(chatClient).prompt();
  }

  @Test
  void doesNotFallbackForNonTransientProviderFailure() {
    given(responseSpec.entity(GeneratedBookEnrichment.class))
        .willThrow(new NonTransientAiException("authentication detail"));

    assertThatThrownBy(() -> generator.generate("rendered prompt", tools))
        .isInstanceOfSatisfying(ModelExecutionException.class, failure -> {
          assertThat(failure.category()).isEqualTo(ModelFailureCategory.CONFIGURATION_ERROR);
          assertThat(failure.eligible()).isFalse();
        });
  }
}
