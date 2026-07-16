package dev.iseif.listingquality.service;

import dev.iseif.listingquality.model.ListingReview;
import dev.iseif.listingquality.service.exception.InvalidAiResponseException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class ListingReviewValidator {

  private final Validator validator;

  public ListingReviewValidator(Validator validator) {
    this.validator = validator;
  }

  public ListingReview validate(ListingReview review) {
    if (review == null) {
      throw new InvalidAiResponseException();
    }

    Set<ConstraintViolation<ListingReview>> violations = validator.validate(review);
    if (!violations.isEmpty()) {
      throw new InvalidAiResponseException(new ConstraintViolationException(violations));
    }

    return review;
  }
}
