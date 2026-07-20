package dev.iseif.listingquality.enrichment.live;

import dev.iseif.listingquality.enrichment.model.shoe.ShoeColorOutcome;
import dev.iseif.listingquality.enrichment.service.shoe.ShoeColorValidationFailure;

import java.util.*;

record ShoeColorQualificationReport(
    int totalRuns,
    int schemaFailures,
    int invalidEvidenceReferences,
    int backgroundPropFalsePositives,
    double primaryAccuracy,
    double colorPrecision,
    double colorRecall,
    double outsoleNegativeAccuracy,
    double consistency,
    long medianLatencyMs,
    long p95LatencyMs,
    List<String> failedCaseIds) {

  ShoeColorQualificationReport {
    failedCaseIds = List.copyOf(failedCaseIds);
  }

  static ShoeColorQualificationReport calculate(
      List<ShoeColorQualificationObservation> observations) {
    int schemaFailures = 0;
    int invalidEvidence = 0;
    int propFalsePositives = 0;
    int positiveRuns = 0;
    int correctPrimary = 0;
    int truePositiveColors = 0;
    int falsePositiveColors = 0;
    int falseNegativeColors = 0;
    int outsoleRuns = 0;
    int correctOutsoleRuns = 0;
    var latencies = new ArrayList<Long>();
    var failures = new java.util.TreeSet<String>();

    for (ShoeColorQualificationObservation observation : observations) {
      latencies.add(observation.latencyMs());
      if (observation.schemaFailure()) {
        schemaFailures++;
        failures.add(observation.caseId());
      }
      if (isEvidenceFailure(observation.validationFailure())) {
        invalidEvidence++;
        failures.add(observation.caseId());
      }
      if (observation.backgroundPropNegative()
          && observation.actualColors().stream().anyMatch(observation.forbiddenColors()::contains)) {
        propFalsePositives++;
        failures.add(observation.caseId());
      }
      if (observation.expectedOutcome() == ShoeColorOutcome.COLORS_OBSERVED) {
        positiveRuns++;
        if (observation.expectedPrimary() == observation.actualPrimary()) {
          correctPrimary++;
        } else {
          failures.add(observation.caseId());
        }
        truePositiveColors += intersectionSize(
            observation.expectedColors(), observation.actualColors());
        falsePositiveColors += observation.actualColors().stream()
            .filter(color -> !observation.expectedColors().contains(color))
            .count();
        falseNegativeColors += observation.expectedColors().stream()
            .filter(color -> !observation.actualColors().contains(color))
            .count();
      }
      if (observation.outsoleNegative()) {
        outsoleRuns++;
        boolean correct = observation.actualIgnoredImageIds()
            .containsAll(observation.expectedIgnoredImageIds())
            && observation.actualColors().stream()
                .noneMatch(observation.forbiddenColors()::contains);
        if (correct) {
          correctOutsoleRuns++;
        } else {
          failures.add(observation.caseId());
        }
      }
    }

    return new ShoeColorQualificationReport(
        observations.size(),
        schemaFailures,
        invalidEvidence,
        propFalsePositives,
        ratio(correctPrimary, positiveRuns),
        ratio(truePositiveColors, truePositiveColors + falsePositiveColors),
        ratio(truePositiveColors, truePositiveColors + falseNegativeColors),
        ratio(correctOutsoleRuns, outsoleRuns),
        consistency(observations),
        percentile(latencies, 0.5),
        percentile(latencies, 0.95),
        List.copyOf(failures));
  }

  boolean passesReleaseGate() {
    return invalidEvidenceReferences == 0
        && schemaFailures == 0
        && outsoleNegativeAccuracy == 1.0
        && backgroundPropFalsePositives == 0
        && primaryAccuracy >= 0.9
        && colorPrecision >= 0.9
        && colorRecall >= 0.85;
  }

  private static boolean isEvidenceFailure(ShoeColorValidationFailure failure) {
    return failure == ShoeColorValidationFailure.EVIDENCE_REFERENCE_INVALID
        || failure == ShoeColorValidationFailure.IGNORED_IMAGE_USED_AS_EVIDENCE;
  }

  private static int intersectionSize(java.util.Set<?> left, java.util.Set<?> right) {
    return (int) left.stream().filter(right::contains).count();
  }

  private static double ratio(long numerator, long denominator) {
    return denominator == 0 ? 1.0 : (double) numerator / denominator;
  }

  private static double consistency(List<ShoeColorQualificationObservation> observations) {
    Map<String, Map<String, Integer>> signatures = new HashMap<>();
    for (ShoeColorQualificationObservation observation : observations) {
      String signature = observation.actualOutcome()
          + "|" + observation.actualPrimary()
          + "|" + observation.actualColors().stream().sorted().toList()
          + "|" + observation.actualIgnoredImageIds().stream().sorted().toList();
      signatures.computeIfAbsent(observation.caseId(), ignored -> new HashMap<>())
          .merge(signature, 1, Integer::sum);
    }
    return signatures.values().stream()
        .mapToDouble(counts -> {
          int total = counts.values().stream().mapToInt(Integer::intValue).sum();
          int mode = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
          return ratio(mode, total);
        })
        .average()
        .orElse(1.0);
  }

  private static long percentile(List<Long> values, double percentile) {
    if (values.isEmpty()) {
      return 0;
    }
    List<Long> sorted = values.stream().sorted(Comparator.naturalOrder()).toList();
    int index = Math.max(0, (int) Math.ceil(percentile * sorted.size()) - 1);
    return sorted.get(index);
  }
}
