package dev.iseif.listingquality.evaluation.result;

import dev.iseif.listingquality.evaluation.api.ListingReviewPayload;

public record SampleResult(
    String caseId,
    int repetition,
    SampleStatus status,
    ListingReviewPayload review,
    SampleFailure failure,
    long durationMs) {

  public SampleResult {
    if (caseId == null || caseId.isBlank()) {
      throw new IllegalArgumentException("Sample case ID must not be blank");
    }
    if (repetition < 1) {
      throw new IllegalArgumentException("Sample repetition must be positive");
    }
    if (durationMs < 0) {
      throw new IllegalArgumentException("Sample duration must not be negative");
    }
    if (status == SampleStatus.SUCCEEDED && (review == null || failure != null)) {
      throw new IllegalArgumentException("Successful sample requires a review and no failure");
    }
    if (status == SampleStatus.FAILED && (failure == null || review != null)) {
      throw new IllegalArgumentException("Failed sample requires a failure and no review");
    }
  }
}
