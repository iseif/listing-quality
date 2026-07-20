package dev.iseif.listingquality.enrichment.live;

import dev.iseif.listingquality.enrichment.model.shoe.ShoeColor;
import dev.iseif.listingquality.enrichment.model.shoe.ShoeColorOutcome;
import dev.iseif.listingquality.enrichment.service.shoe.ShoeColorValidationFailure;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ShoeColorQualificationMetricsTest {

  @Test
  void calculatesDeterministicAccuracyPrecisionRecallAndLatency() {
    var observations = List.of(
        positive("green", ShoeColor.GREEN, Set.of(ShoeColor.GREEN, ShoeColor.WHITE), 10),
        observation(
            "green",
            ShoeColor.GREEN,
            Set.of(ShoeColor.GREEN, ShoeColor.WHITE),
            Set.of(),
            false,
            false,
            ShoeColor.RED,
            Set.of(ShoeColor.RED, ShoeColor.WHITE),
            Set.of(),
            false,
            null,
            20),
        observation(
            "outsole",
            null,
            Set.of(),
            Set.of("IMAGE_1"),
            true,
            true,
            ShoeColor.RED,
            Set.of(ShoeColor.RED),
            Set.of(),
            true,
            ShoeColorValidationFailure.EVIDENCE_REFERENCE_INVALID,
            100));

    ShoeColorQualificationReport report = ShoeColorQualificationReport.calculate(observations);

    assertThat(report.totalRuns()).isEqualTo(3);
    assertThat(report.primaryAccuracy()).isEqualTo(0.5);
    assertThat(report.colorPrecision()).isEqualTo(0.75);
    assertThat(report.colorRecall()).isEqualTo(0.75);
    assertThat(report.outsoleNegativeAccuracy()).isZero();
    assertThat(report.backgroundPropFalsePositives()).isEqualTo(1);
    assertThat(report.schemaFailures()).isEqualTo(1);
    assertThat(report.invalidEvidenceReferences()).isEqualTo(1);
    assertThat(report.consistency()).isEqualTo(0.75);
    assertThat(report.medianLatencyMs()).isEqualTo(20);
    assertThat(report.p95LatencyMs()).isEqualTo(100);
    assertThat(report.passesReleaseGate()).isFalse();
  }

  @Test
  void passesOnlyWhenEveryPredeterminedReleaseThresholdIsMet() {
    var observations = List.of(
        positive("green", ShoeColor.GREEN, Set.of(ShoeColor.GREEN, ShoeColor.WHITE), 10),
        positive("green", ShoeColor.GREEN, Set.of(ShoeColor.GREEN, ShoeColor.WHITE), 11),
        observation(
            "outsole",
            null,
            Set.of(),
            Set.of("IMAGE_1"),
            true,
            false,
            null,
            Set.of(),
            Set.of("IMAGE_1"),
            false,
            null,
            12),
        observation(
            "prop",
            ShoeColor.BLACK,
            Set.of(ShoeColor.BLACK, ShoeColor.WHITE),
            Set.of(),
            false,
            true,
            ShoeColor.BLACK,
            Set.of(ShoeColor.BLACK, ShoeColor.WHITE),
            Set.of(),
            false,
            null,
            13));

    ShoeColorQualificationReport report = ShoeColorQualificationReport.calculate(observations);

    assertThat(report.passesReleaseGate()).isTrue();
  }

  private ShoeColorQualificationObservation positive(
      String caseId,
      ShoeColor primary,
      Set<ShoeColor> colors,
      long latency) {
    return observation(
        caseId, primary, colors, Set.of(), false, false,
        primary, colors, Set.of(), false, null, latency);
  }

  private ShoeColorQualificationObservation observation(
      String caseId,
      ShoeColor expectedPrimary,
      Set<ShoeColor> expectedColors,
      Set<String> expectedIgnored,
      boolean outsoleNegative,
      boolean backgroundPropNegative,
      ShoeColor actualPrimary,
      Set<ShoeColor> actualColors,
      Set<String> actualIgnored,
      boolean schemaFailure,
      ShoeColorValidationFailure validationFailure,
      long latency) {
    return new ShoeColorQualificationObservation(
        caseId,
        expectedPrimary == null ? ShoeColorOutcome.INCONCLUSIVE : ShoeColorOutcome.COLORS_OBSERVED,
        expectedPrimary,
        expectedColors,
        expectedIgnored,
        backgroundPropNegative ? Set.of(ShoeColor.RED) : Set.of(),
        outsoleNegative,
        backgroundPropNegative,
        actualPrimary == null ? ShoeColorOutcome.INCONCLUSIVE : ShoeColorOutcome.COLORS_OBSERVED,
        actualPrimary,
        actualColors,
        actualIgnored,
        schemaFailure,
        validationFailure,
        latency);
  }
}
