package dev.iseif.listingquality.evaluation.grade;

public record SemanticEvaluation(
    int expectedConceptCoverage,
    int unsupportedIssueScore,
    int groundednessScore,
    int suggestionUsefulness,
    boolean pass,
    String explanation) {

  public SemanticEvaluation {
    validate("expectedConceptCoverage", expectedConceptCoverage);
    validate("unsupportedIssueScore", unsupportedIssueScore);
    validate("groundednessScore", groundednessScore);
    validate("suggestionUsefulness", suggestionUsefulness);
    if (explanation == null || explanation.isBlank()) {
      throw new IllegalArgumentException("Semantic evaluation explanation must not be blank");
    }
  }

  private static void validate(String name, int value) {
    if (value < 0 || value > 100) {
      throw new IllegalArgumentException(name + " must be between 0 and 100");
    }
  }
}
