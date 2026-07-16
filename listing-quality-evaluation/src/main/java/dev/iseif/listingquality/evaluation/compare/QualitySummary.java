package dev.iseif.listingquality.evaluation.compare;

public record QualitySummary(
    Double expectedConceptCoverage,
    double semanticJudgeCoverage,
    QualityStatus qualityStatus,
    String semanticEvidenceVersion) {

  public static QualitySummary from(
      Double expectedConceptCoverage,
      double semanticJudgeCoverage) {
    QualityStatus status;
    if (semanticJudgeCoverage < 1.0 || expectedConceptCoverage == null) {
      status = QualityStatus.NOT_EVALUATED;
    } else {
      status = expectedConceptCoverage >= 80.0
          ? QualityStatus.PASS
          : QualityStatus.FAIL;
    }
    return new QualitySummary(
        expectedConceptCoverage, semanticJudgeCoverage, status, "v1");
  }
}
