package dev.iseif.listingquality.evaluation.compare;

import dev.iseif.listingquality.evaluation.grade.HumanReviewOutcome;

import java.util.List;

public record SampleAssessment(
    String candidate,
    String caseId,
    int repetition,
    boolean validResponse,
    boolean scoreInRange,
    HumanReviewOutcome humanReviewOutcome,
    List<String> matchedForbiddenClaims,
    SemanticStatus semanticStatus,
    Integer expectedConceptCoverage,
    String semanticFeedback) {

  public SampleAssessment {
    matchedForbiddenClaims = List.copyOf(matchedForbiddenClaims);
  }
}
