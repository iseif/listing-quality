package dev.iseif.listingquality.service;

import dev.iseif.listingquality.model.ListingDraft;
import dev.iseif.listingquality.model.ListingReview;
import dev.iseif.listingquality.prompt.ListingReviewPrompt;
import dev.iseif.listingquality.service.exception.InvalidAiResponseException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ListingReviewServiceTest {

  private static ValidatorFactory factory;

  @BeforeAll
  static void openFactory() {
    factory = Validation.buildDefaultValidatorFactory();
  }

  @AfterAll
  static void closeFactory() {
    factory.close();
  }

  private final ListingReviewPrompt prompt = new ListingReviewPrompt(
      new ClassPathResource("prompts/listing-review.st"), JsonMapper.builder().build());
  private final ListingReviewValidator validator =
      new ListingReviewValidator(factory.getValidator());

  private final ListingDraft draft = new ListingDraft(
      "Wireless keyboard", "Nice keyboard", "Computer accessories",
      new BigDecimal("45.00"), Map.of("brand", "KeyPro"));

  @Test
  void rendersThePromptAndReturnsAValidatedReview() {
    AtomicReference<String> capturedPrompt = new AtomicReference<>();
    ListingReview expected = new ListingReview(70, List.of("layout"), List.of(), List.of("Add layout"), false);
    ListingReviewGenerator generator = renderedPrompt -> {
      capturedPrompt.set(renderedPrompt);
      return expected;
    };

    ListingReview result = new ListingReviewService(prompt, generator, validator).review(draft);

    assertThat(result).isEqualTo(expected);
    assertThat(capturedPrompt.get()).contains("Wireless keyboard").contains("<listing-data>");
  }

  @Test
  void propagatesInvalidResponseWhenTheModelBreaksTheContract() {
    ListingReviewGenerator generator =
        renderedPrompt -> new ListingReview(999, List.of(), List.of(), List.of(), false);

    assertThatThrownBy(() -> new ListingReviewService(prompt, generator, validator).review(draft))
        .isInstanceOf(InvalidAiResponseException.class);
  }
}
