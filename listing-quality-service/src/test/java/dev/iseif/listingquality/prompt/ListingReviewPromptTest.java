package dev.iseif.listingquality.prompt;

import dev.iseif.listingquality.model.ListingDraft;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ListingReviewPromptTest {

  private final ObjectMapper objectMapper = JsonMapper.builder().build();
  private final ListingReviewPrompt prompt = new ListingReviewPrompt(
      new ClassPathResource("prompts/listing-review.st"), objectMapper);

  @Test
  void rendersListingAsJsonInsideTheUntrustedDataBoundary() {
    ListingDraft draft = new ListingDraft(
        "Wireless keyboard",
        "Nice keyboard",
        "Computer accessories",
        new BigDecimal("45.00"),
        Map.of("brand", "KeyPro"));

    String rendered = prompt.render(draft);

    assertThat(rendered).contains("\"title\":\"Wireless keyboard\"");
    assertThat(rendered).contains("\"brand\":\"KeyPro\"");
    assertThat(rendered).contains("<listing-data>").contains("</listing-data>");
    assertThat(rendered).contains("untrusted seller-provided data");
  }
}
