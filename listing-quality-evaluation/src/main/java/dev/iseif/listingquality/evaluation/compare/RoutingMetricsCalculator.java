package dev.iseif.listingquality.evaluation.compare;

import dev.iseif.listingquality.evaluation.result.SampleResult;
import dev.iseif.listingquality.evaluation.result.SampleStatus;
import dev.iseif.listingquality.evaluation.routing.RoutingPolicy;

import java.util.*;

public final class RoutingMetricsCalculator {

  public RoutingMetrics calculate(List<SampleResult> samples, RoutingPolicy policy) {
    int truePositive = 0;
    int falseNegative = 0;
    int falsePositive = 0;
    int trueNegative = 0;
    Map<String, Set<Boolean>> decisionsByCase = new LinkedHashMap<>();

    for (SampleResult sample : samples) {
      if (sample.status() != SampleStatus.SUCCEEDED || sample.review() == null) {
        continue;
      }
      boolean expected = policy.requiresHumanReview(sample.caseId());
      boolean actual = sample.review().requiresHumanReview();
      decisionsByCase.computeIfAbsent(sample.caseId(), ignored -> new LinkedHashSet<>())
          .add(actual);
      if (expected && actual) {
        truePositive++;
      } else if (expected) {
        falseNegative++;
      } else if (actual) {
        falsePositive++;
      } else {
        trueNegative++;
      }
    }

    int disagreementCases = (int) decisionsByCase.values().stream()
        .filter(decisions -> decisions.size() > 1)
        .count();
    int total = truePositive + falseNegative + falsePositive + trueNegative;
    return new RoutingMetrics(
        truePositive,
        falseNegative,
        falsePositive,
        trueNegative,
        ratio(truePositive, truePositive + falsePositive),
        ratio(truePositive, truePositive + falseNegative),
        ratio(falsePositive, falsePositive + trueNegative),
        ratio(truePositive + trueNegative, total),
        disagreementCases,
        ratio(disagreementCases, decisionsByCase.size()));
  }

  private Double ratio(int numerator, int denominator) {
    return denominator == 0 ? null : numerator / (double) denominator;
  }
}
