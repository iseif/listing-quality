package dev.iseif.listingquality.evaluation.grade;

import dev.iseif.listingquality.evaluation.result.SampleResult;
import dev.iseif.listingquality.evaluation.result.SampleStatus;

public final class HumanReviewGrader {

  public HumanReviewOutcome classify(boolean expected, SampleResult sample) {
    if (sample.status() != SampleStatus.SUCCEEDED || sample.review() == null) {
      return HumanReviewOutcome.NOT_EVALUATED;
    }
    boolean actual = sample.review().requiresHumanReview();
    if (expected && actual) {
      return HumanReviewOutcome.TRUE_POSITIVE;
    }
    if (expected) {
      return HumanReviewOutcome.FALSE_NEGATIVE;
    }
    if (actual) {
      return HumanReviewOutcome.FALSE_POSITIVE;
    }
    return HumanReviewOutcome.TRUE_NEGATIVE;
  }
}
