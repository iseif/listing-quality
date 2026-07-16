package dev.iseif.listingquality.evaluation.grade;

import java.util.List;

public record SampleGrade(
    boolean validResponse,
    boolean scoreInRange,
    HumanReviewOutcome humanReviewOutcome,
    List<String> matchedForbiddenClaims) {

  public SampleGrade {
    matchedForbiddenClaims = List.copyOf(matchedForbiddenClaims);
  }
}
