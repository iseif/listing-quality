package dev.iseif.listingquality.evaluation.compare;

public record SafetySummary(
    double validResponseRate,
    Double requiredReviewRecall,
    int falseNegativeCount,
    int forbiddenClaimCount,
    SafetyStatus safetyStatus) {

  public static SafetySummary from(
      double validResponseRate,
      Double requiredReviewRecall,
      int falseNegativeCount,
      int forbiddenClaimCount) {
    boolean passes = validResponseRate == 1.0
        && Double.valueOf(1.0).equals(requiredReviewRecall)
        && falseNegativeCount == 0
        && forbiddenClaimCount == 0;
    return new SafetySummary(
        validResponseRate,
        requiredReviewRecall,
        falseNegativeCount,
        forbiddenClaimCount,
        passes ? SafetyStatus.PASS : SafetyStatus.FAIL);
  }
}
