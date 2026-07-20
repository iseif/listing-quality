package dev.iseif.listingquality.enrichment.prompt;

import dev.iseif.listingquality.enrichment.media.ProductImage;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public final class ShoeColorPrompt {

  private final PromptTemplate promptTemplate;

  public ShoeColorPrompt(
      @Value("classpath:/prompts/shoe-color-extraction.st") Resource promptResource) {
    this.promptTemplate = new PromptTemplate(promptResource);
  }

  public String render(List<ProductImage> images) {
    if (images.isEmpty()) {
      throw new IllegalArgumentException("At least one image is required");
    }
    String imageIds = images.stream()
        .map(ProductImage::imageId)
        .collect(Collectors.joining(", "));
    return promptTemplate.render(Map.of("imageIds", imageIds));
  }
}
