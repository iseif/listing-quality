package dev.iseif.listingquality.evaluation.result;

public record TokenUsage(Long inputTokens, Long outputTokens, Long totalTokens) {

  public static TokenUsage notReported() {
    return new TokenUsage(null, null, null);
  }

  public TokenUsage minus(TokenUsage baseline) {
    return new TokenUsage(
        subtract(inputTokens, baseline.inputTokens),
        subtract(outputTokens, baseline.outputTokens),
        subtract(totalTokens, baseline.totalTokens));
  }

  private Long subtract(Long value, Long baseline) {
    if (value == null || baseline == null) {
      return null;
    }
    return Math.max(0L, value - baseline);
  }
}
