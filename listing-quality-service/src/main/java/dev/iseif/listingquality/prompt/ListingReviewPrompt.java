package dev.iseif.listingquality.prompt;

import dev.iseif.listingquality.model.ListingDraft;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@Component
public class ListingReviewPrompt {

  private final PromptTemplate promptTemplate;
  private final ObjectMapper objectMapper;

  public ListingReviewPrompt(
      @Value("classpath:/prompts/listing-review.st") Resource promptResource,
      ObjectMapper objectMapper) {
    this.promptTemplate = new PromptTemplate(promptResource);
    this.objectMapper = objectMapper;
  }

  public String render(ListingDraft listingDraft) {
    String listingJson = objectMapper.writeValueAsString(listingDraft);
    return promptTemplate.render(Map.of("listingJson", listingJson));
  }
}
