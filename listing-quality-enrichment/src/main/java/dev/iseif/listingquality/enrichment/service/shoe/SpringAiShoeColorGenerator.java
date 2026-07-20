package dev.iseif.listingquality.enrichment.service.shoe;

import dev.iseif.listingquality.enrichment.media.ProductImage;
import dev.iseif.listingquality.enrichment.model.shoe.GeneratedShoeColorExtraction;
import dev.iseif.listingquality.enrichment.service.execution.ModelExecutionException;
import dev.iseif.listingquality.enrichment.service.execution.ModelFailureCategory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.StructuredOutputValidationAdvisor;
import org.springframework.ai.content.Media;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.core.io.ByteArrayResource;

import java.util.List;
import java.util.Objects;

public final class SpringAiShoeColorGenerator implements ShoeColorGenerator {

  private final String providerId;
  private final ChatClient chatClient;
  private final StructuredOutputValidationAdvisor validationAdvisor;

  public SpringAiShoeColorGenerator(
      String providerId,
      ChatClient chatClient,
      int structuredOutputAttempts) {
    this.providerId = Objects.requireNonNull(providerId);
    this.chatClient = Objects.requireNonNull(chatClient);
    if (structuredOutputAttempts < 1) {
      throw new IllegalArgumentException("structuredOutputAttempts must be at least one");
    }
    this.validationAdvisor = StructuredOutputValidationAdvisor.builder()
        .outputType(GeneratedShoeColorExtraction.class)
        .maxRepeatAttempts(structuredOutputAttempts - 1)
        .build();
  }

  @Override
  public GeneratedShoeColorExtraction generate(String prompt, List<ProductImage> images) {
    Media[] media = images.stream()
        .map(image -> Media.builder()
            .id(image.imageId())
            .mimeType(image.mediaType())
            .data(new ByteArrayResource(image.bytes()))
            .build())
        .toArray(Media[]::new);

    try {
      return chatClient.prompt()
          .user(user -> user.text(prompt).media(media))
          .advisors(validationAdvisor)
          .call()
          .entity(GeneratedShoeColorExtraction.class);
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
