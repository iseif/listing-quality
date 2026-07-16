package dev.iseif.listingquality.evaluation.compare;

import dev.iseif.listingquality.evaluation.api.ListingReviewPayload;
import dev.iseif.listingquality.evaluation.result.SampleResult;
import dev.iseif.listingquality.evaluation.result.SampleStatus;
import dev.iseif.listingquality.evaluation.routing.RoutingPolicy;
import dev.iseif.listingquality.evaluation.routing.RoutingPolicyCase;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RoutingMetricsCalculatorTest {

  private final RoutingMetricsCalculator calculator = new RoutingMetricsCalculator();

  @Test
  void calculatesEveryConfusionMatrixOutcomeAndRate() {
    RoutingPolicy policy = policy(Map.of("positive", true, "negative", false));
    List<SampleResult> samples = List.of(
        sample("positive", 1, true),
        sample("positive", 2, true),
        sample("positive", 3, false),
        sample("negative", 1, true),
        sample("negative", 2, false),
        sample("negative", 3, false));

    RoutingMetrics metrics = calculator.calculate(samples, policy);

    assertThat(metrics.truePositiveCount()).isEqualTo(2);
    assertThat(metrics.falseNegativeCount()).isEqualTo(1);
    assertThat(metrics.falsePositiveCount()).isEqualTo(1);
    assertThat(metrics.trueNegativeCount()).isEqualTo(2);
    assertThat(metrics.precision()).isEqualTo(2.0 / 3.0);
    assertThat(metrics.recall()).isEqualTo(2.0 / 3.0);
    assertThat(metrics.falsePositiveRate()).isEqualTo(1.0 / 3.0);
    assertThat(metrics.accuracy()).isEqualTo(4.0 / 6.0);
    assertThat(metrics.disagreementCaseCount()).isEqualTo(2);
    assertThat(metrics.disagreementCaseRate()).isEqualTo(1.0);
  }

  @Test
  void returnsNullForUndefinedRatios() {
    RoutingMetrics noPredictedPositive = calculator.calculate(
        List.of(sample("negative", 1, false)), policy(Map.of("negative", false)));
    RoutingMetrics noPolicyPositive = calculator.calculate(
        List.of(sample("negative", 1, true)), policy(Map.of("negative", false)));
    RoutingMetrics noPolicyNegative = calculator.calculate(
        List.of(sample("positive", 1, true)), policy(Map.of("positive", true)));

    assertThat(noPredictedPositive.precision()).isNull();
    assertThat(noPolicyPositive.recall()).isNull();
    assertThat(noPolicyNegative.falsePositiveRate()).isNull();
  }

  @Test
  void countsDisagreementIndependentlyFromCorrectness() {
    RoutingMetrics metrics = calculator.calculate(
        List.of(
            sample("positive", 1, false),
            sample("positive", 2, false),
            sample("positive", 3, true)),
        policy(Map.of("positive", true)));

    assertThat(metrics.disagreementCaseCount()).isOne();
    assertThat(metrics.disagreementCaseRate()).isEqualTo(1.0);
  }

  @Test
  void derivesSafetyAndQualityStatusesIndependently() {
    assertThat(SafetySummary.from(1.0, 1.0, 0, 0).safetyStatus())
        .isEqualTo(SafetyStatus.PASS);
    assertThat(SafetySummary.from(1.0, 1.0, 0, 1).safetyStatus())
        .isEqualTo(SafetyStatus.FAIL);
    assertThat(SafetySummary.from(1.0, null, 0, 0).safetyStatus())
        .isEqualTo(SafetyStatus.FAIL);
    assertThat(QualitySummary.from(85.0, 1.0).qualityStatus())
        .isEqualTo(QualityStatus.PASS);
    assertThat(QualitySummary.from(79.9, 1.0).qualityStatus())
        .isEqualTo(QualityStatus.FAIL);
    assertThat(QualitySummary.from(90.0, 35.0 / 36.0).qualityStatus())
        .isEqualTo(QualityStatus.NOT_EVALUATED);
  }

  private RoutingPolicy policy(Map<String, Boolean> decisions) {
    Map<String, RoutingPolicyCase> cases = new LinkedHashMap<>();
    decisions.forEach((caseId, required) -> cases.put(
        caseId, new RoutingPolicyCase(caseId, required, "test policy")));
    return new RoutingPolicy("test-policy", "a".repeat(64), cases);
  }

  private SampleResult sample(String caseId, int repetition, boolean requiresReview) {
    return new SampleResult(
        caseId,
        repetition,
        SampleStatus.SUCCEEDED,
        new ListingReviewPayload(50, List.of(), List.of(), List.of(), requiresReview),
        null,
        10);
  }
}
