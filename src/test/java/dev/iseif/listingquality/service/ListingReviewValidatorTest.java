package dev.iseif.listingquality.service;

import dev.iseif.listingquality.model.ListingReview;
import dev.iseif.listingquality.service.exception.InvalidAiResponseException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ListingReviewValidatorTest {

  private static ValidatorFactory factory;

  @BeforeAll
  static void openFactory() {
    factory = Validation.buildDefaultValidatorFactory();
  }

  @AfterAll
  static void closeFactory() {
    factory.close();
  }

  private ListingReviewValidator newValidator() {
    Validator jakartaValidator = factory.getValidator();
    return new ListingReviewValidator(jakartaValidator);
  }

  @Test
  void acceptsAValidReview() {
    ListingReview review = new ListingReview(80, List.of(), List.of(), List.of("Add layout"), false);

    assertThat(newValidator().validate(review)).isSameAs(review);
  }

  @Test
  void rejectsNullReview() {
    assertThatThrownBy(() -> newValidator().validate(null))
        .isInstanceOf(InvalidAiResponseException.class);
  }

  @Test
  void rejectsScoreOutOfRange() {
    ListingReview review = new ListingReview(150, List.of(), List.of(), List.of(), false);

    assertThatThrownBy(() -> newValidator().validate(review))
        .isInstanceOf(InvalidAiResponseException.class);
  }

  @Test
  void rejectsNullList() {
    ListingReview review = new ListingReview(50, null, List.of(), List.of(), false);

    assertThatThrownBy(() -> newValidator().validate(review))
        .isInstanceOf(InvalidAiResponseException.class);
  }

  @Test
  void rejectsBlankSuggestion() {
    ListingReview review = new ListingReview(50, List.of(), List.of(), List.of("   "), false);

    assertThatThrownBy(() -> newValidator().validate(review))
        .isInstanceOf(InvalidAiResponseException.class);
  }
}
