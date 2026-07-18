package dev.iseif.listingquality.enrichment.service.book;

import dev.iseif.listingquality.enrichment.catalog.book.BookCatalogClient;
import dev.iseif.listingquality.enrichment.catalog.book.BookCatalogEvidenceLedger;
import dev.iseif.listingquality.enrichment.catalog.book.BookCatalogRecord;
import dev.iseif.listingquality.enrichment.catalog.book.BookCatalogTools;
import dev.iseif.listingquality.enrichment.service.execution.ModelExecutionException;
import dev.iseif.listingquality.enrichment.service.execution.ModelFailureCategory;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpringAiBookEnrichmentGeneratorRetryTest {

  @Test
  void limitsSchemaRegenerationToTheConfiguredTotalAttempts() {
    AtomicInteger modelCalls = new AtomicInteger();
    ChatModel chatModel = prompt -> {
      modelCalls.incrementAndGet();
      return new ChatResponse(List.of(
          new Generation(new AssistantMessage("{\"proposals\":["))));
    };
    ChatClient chatClient = ChatClient.builder(chatModel).build();
    BookCatalogTools tools = new BookCatalogTools(
        new EmptyBookCatalogClient(), new BookCatalogEvidenceLedger());
    SpringAiBookEnrichmentGenerator generator =
        new SpringAiBookEnrichmentGenerator("gemini", chatClient, 2);

    assertThatThrownBy(() -> generator.generate("rendered prompt", tools))
        .isInstanceOfSatisfying(ModelExecutionException.class, failure ->
            assertThat(failure.category()).isEqualTo(ModelFailureCategory.INVALID_MODEL_OUTPUT));
    assertThat(modelCalls).hasValue(2);
  }

  private static final class EmptyBookCatalogClient implements BookCatalogClient {

    @Override
    public Optional<BookCatalogRecord> findByIsbn(String normalizedIsbn) {
      return Optional.empty();
    }

    @Override
    public List<BookCatalogRecord> search(String title, String author, int limit) {
      return List.of();
    }
  }
}
