package dev.iseif.listingquality.enrichment.prompt;

import dev.iseif.listingquality.enrichment.model.book.BookEnrichmentRequest;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@Component
public final class BookEnrichmentPrompt {

  private final PromptTemplate promptTemplate;
  private final ObjectMapper objectMapper;

  public BookEnrichmentPrompt(
      @Value("classpath:/prompts/book-enrichment.st") Resource promptResource,
      ObjectMapper objectMapper) {
    this.promptTemplate = new PromptTemplate(promptResource);
    this.objectMapper = objectMapper;
  }

  public String render(BookEnrichmentRequest request) {
    String listingJson = objectMapper.writeValueAsString(request);
    return promptTemplate.render(Map.of("listingJson", listingJson));
  }
}
