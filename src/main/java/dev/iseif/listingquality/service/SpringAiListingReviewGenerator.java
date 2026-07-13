package dev.iseif.listingquality.service;

import dev.iseif.listingquality.model.ListingReview;
import dev.iseif.listingquality.service.exception.AiProviderException;
import dev.iseif.listingquality.service.exception.AiProviderUnavailableException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.EntityParamSpec;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.stereotype.Component;

@Component
public class SpringAiListingReviewGenerator implements ListingReviewGenerator {

  private final ChatClient chatClient;

  public SpringAiListingReviewGenerator(ChatClient listingReviewChatClient) {
    this.chatClient = listingReviewChatClient;
  }

  @Override
  public ListingReview generate(String prompt) {
    try {
      return chatClient.prompt()
          .user(prompt)
          .call()
          .entity(ListingReview.class, EntityParamSpec::validateSchema);
    } catch (TransientAiException exception) {
      throw new AiProviderUnavailableException(exception);
    } catch (RuntimeException exception) {
      throw new AiProviderException(exception);
    }
  }
}
