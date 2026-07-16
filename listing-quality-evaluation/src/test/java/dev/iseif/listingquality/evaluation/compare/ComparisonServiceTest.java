package dev.iseif.listingquality.evaluation.compare;

import dev.iseif.listingquality.evaluation.api.ListingReviewPayload;
import dev.iseif.listingquality.evaluation.dataset.DatasetLoader;
import dev.iseif.listingquality.evaluation.dataset.EvaluationCase;
import dev.iseif.listingquality.evaluation.dataset.EvaluationDataset;
import dev.iseif.listingquality.evaluation.grade.HumanReviewOutcome;
import dev.iseif.listingquality.evaluation.report.CostStatus;
import dev.iseif.listingquality.evaluation.report.PriceTable;
import dev.iseif.listingquality.evaluation.result.*;
import dev.iseif.listingquality.evaluation.routing.RoutingPolicy;
import dev.iseif.listingquality.evaluation.routing.RoutingPolicyLoader;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.ai.evaluation.EvaluationResponse;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ComparisonServiceTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final DatasetLoader datasetLoader = new DatasetLoader(
      objectMapper, Validation.buildDefaultValidatorFactory().getValidator());
  private final EvaluationDataset dataset = datasetLoader.load("listing-quality-v1");
  private final PriceTable prices = PriceTable.load(
      objectMapper, "pricing/model-prices-2026-07-15.json");
  private final ComparisonService service = new ComparisonService(
      datasetLoader, prices, objectMapper, new RunCompatibilityValidator());
  private final RoutingPolicy routingPolicy = new RoutingPolicyLoader(objectMapper)
      .load("listing-quality-routing-v2", dataset);

  @Test
  void buildsIndependentSafetyQualityAndOperationalScorecards() {
    CandidateRun run = passingRunFor(routingPolicy, "openai-gpt-5.6", "openai", "gpt-5.6");

    ScorecardReport report = service.compare(
        List.of(run), routingPolicy, request ->
            new EvaluationResponse(true, 0.85f, "Covered expected concepts", Map.of(
                "expectedConceptCoverage", 85)),
        List.of());

    CandidateScorecard scorecard = report.candidates().getFirst();
    assertThat(report.schemaVersion()).isEqualTo(2);
    assertThat(report.routingPolicyVersion()).isEqualTo("listing-quality-routing-v2");
    assertThat(scorecard.safety().safetyStatus()).isEqualTo(SafetyStatus.PASS);
    assertThat(scorecard.quality().qualityStatus()).isEqualTo(QualityStatus.PASS);
    assertThat(scorecard.operations().truePositiveCount()).isEqualTo(24);
    assertThat(scorecard.operations().trueNegativeCount()).isEqualTo(12);
    assertThat(scorecard.operations().humanReviewPrecision()).isEqualTo(1.0);
    assertThat(scorecard.operations().disagreementCaseCount()).isZero();
  }

  @Test
  void falsePositiveChangesOperationsWithoutFailingSafety() {
    CandidateRun passing = passingRunFor(
        routingPolicy, "openai-gpt-5.6", "openai", "gpt-5.6");
    List<SampleResult> samples = new ArrayList<>(passing.samples());
    SampleResult original = samples.getFirst();
    samples.set(0, withReviewDecision(original, true));

    CandidateScorecard scorecard = service.compare(
        List.of(new CandidateRun(passing.manifest(), samples)),
        routingPolicy,
        request -> new EvaluationResponse(true, 0.85f, "Covered", Map.of(
            "expectedConceptCoverage", 85)),
        List.of()).candidates().getFirst();

    assertThat(scorecard.safety().safetyStatus()).isEqualTo(SafetyStatus.PASS);
    assertThat(scorecard.operations().falsePositiveCount()).isOne();
    assertThat(scorecard.operations().humanReviewPrecision()).isLessThan(1.0);
    assertThat(scorecard.operations().disagreementCaseCount()).isOne();
  }

  @Test
  void falseNegativeFailsOnlyTheSafetyDimension() {
    CandidateRun passing = passingRunFor(
        routingPolicy, "openai-gpt-5.6", "openai", "gpt-5.6");
    List<SampleResult> samples = new ArrayList<>(passing.samples());
    int positiveIndex = 3 * 3;
    SampleResult original = samples.get(positiveIndex);
    samples.set(positiveIndex, withReviewDecision(original, false));

    CandidateScorecard scorecard = service.compare(
        List.of(new CandidateRun(passing.manifest(), samples)),
        routingPolicy,
        request -> new EvaluationResponse(true, 0.85f, "Covered", Map.of(
            "expectedConceptCoverage", 85)),
        List.of()).candidates().getFirst();

    assertThat(scorecard.safety().safetyStatus()).isEqualTo(SafetyStatus.FAIL);
    assertThat(scorecard.safety().falseNegativeCount()).isOne();
    assertThat(scorecard.quality().qualityStatus()).isEqualTo(QualityStatus.PASS);
  }

  @Test
  void completeReusableAssessmentsMakeNoEvaluatorCalls() {
    CandidateRun run = passingRunFor(
        routingPolicy, "openai-gpt-5.6", "openai", "gpt-5.6");
    List<SampleAssessment> assessments = run.samples().stream()
        .map(sample -> new SampleAssessment(
            run.manifest().candidate(), sample.caseId(), sample.repetition(),
            true, true,
            routingPolicy.requiresHumanReview(sample.caseId())
                ? HumanReviewOutcome.TRUE_POSITIVE
                : HumanReviewOutcome.TRUE_NEGATIVE,
            List.of(), SemanticStatus.EVALUATED, 88, "reused"))
        .toList();
    AtomicInteger calls = new AtomicInteger();

    CandidateScorecard scorecard = service.compare(
        List.of(run), routingPolicy, request -> {
          calls.incrementAndGet();
          throw new AssertionError("Evaluator must not be called");
        }, assessments).candidates().getFirst();

    assertThat(calls).hasValue(0);
    assertThat(scorecard.quality().expectedConceptCoverage()).isEqualTo(88.0);
    assertThat(scorecard.quality().qualityStatus()).isEqualTo(QualityStatus.PASS);
  }

  @Test
  void aggregatesACompleteCandidateAcrossAllScorecardDimensions() {
    CandidateRun run = passingRun("openai-gpt-5.6", "openai", "gpt-5.6");

    CandidateScorecard scorecard = service.compare(
        List.of(run), routingPolicy, request ->
            new EvaluationResponse(true, 0.85f, "Covered expected concepts", Map.of(
                "expectedConceptCoverage", 85)))
        .candidates().getFirst();

    assertThat(scorecard.sampleCount()).isEqualTo(36);
    assertThat(scorecard.safety().validResponseRate()).isEqualTo(1.0);
    assertThat(scorecard.safety().requiredReviewRecall()).isEqualTo(1.0);
    assertThat(scorecard.safety().forbiddenClaimCount()).isZero();
    assertThat(scorecard.quality().expectedConceptCoverage()).isEqualTo(85.0);
    assertThat(scorecard.quality().semanticJudgeCoverage()).isEqualTo(1.0);
    assertThat(scorecard.operations().medianLatencyMs()).isEqualTo(19);
    assertThat(scorecard.operations().p95LatencyMs()).isEqualTo(35);
    assertThat(scorecard.operations().cost().status()).isEqualTo(CostStatus.ESTIMATED);
    assertThat(scorecard.operations().cost().usd()).isEqualByComparingTo("0.020000");
  }

  @Test
  void deterministicComparisonRemainsUsableWithoutAJudge() {
    CandidateRun run = passingRun("ollama-gemma4-e4b", "ollama", "gemma4:e4b");

    CandidateScorecard scorecard = service.compare(
        List.of(run), routingPolicy, null).candidates().getFirst();

    assertThat(scorecard.quality().expectedConceptCoverage()).isNull();
    assertThat(scorecard.quality().semanticJudgeCoverage()).isZero();
    assertThat(scorecard.quality().qualityStatus()).isEqualTo(QualityStatus.NOT_EVALUATED);
    assertThat(scorecard.operations().cost().status()).isEqualTo(CostStatus.NOT_APPLICABLE);
  }

  @Test
  void judgeFailureDoesNotDiscardDeterministicResults() {
    CandidateRun run = passingRun("openai-gpt-5.6", "openai", "gpt-5.6");

    CandidateScorecard scorecard = service.compare(List.of(run), routingPolicy, request -> {
      throw new IllegalStateException("judge unavailable");
    }).candidates().getFirst();

    assertThat(scorecard.safety().validResponseRate()).isEqualTo(1.0);
    assertThat(scorecard.quality().semanticJudgeCoverage()).isZero();
    assertThat(scorecard.quality().qualityStatus()).isEqualTo(QualityStatus.NOT_EVALUATED);
  }

  @Test
  void resumesOnlySemanticAssessmentsThatWereNotEvaluated() {
    CandidateRun run = passingRun("openai-gpt-5.6", "openai", "gpt-5.6");
    ScorecardReport first = service.compare(List.of(run), routingPolicy, request ->
        new EvaluationResponse(true, 0.85f, "Covered expected concepts", Map.of(
            "expectedConceptCoverage", 85)));
    List<SampleAssessment> partial = new ArrayList<>(first.assessments());
    SampleAssessment missing = partial.getLast();
    partial.set(partial.size() - 1, new SampleAssessment(
        missing.candidate(), missing.caseId(), missing.repetition(),
        missing.validResponse(), missing.scoreInRange(), missing.humanReviewOutcome(),
        missing.matchedForbiddenClaims(), SemanticStatus.NOT_EVALUATED, null, null));
    AtomicInteger calls = new AtomicInteger();

    ScorecardReport resumed = service.compare(List.of(run), routingPolicy, request -> {
      calls.incrementAndGet();
      return new EvaluationResponse(true, 0.90f, "Retried", Map.of(
          "expectedConceptCoverage", 90));
    }, partial);

    assertThat(calls).hasValue(1);
    assertThat(resumed.candidates().getFirst().quality().semanticJudgeCoverage()).isEqualTo(1.0);
    assertThat(resumed.assessments()).allMatch(
        assessment -> assessment.semanticStatus() == SemanticStatus.EVALUATED);
  }

  @Test
  void incompleteSemanticCoverageCannotPassQuality() {
    CandidateRun run = passingRun("openai-gpt-5.6", "openai", "gpt-5.6");
    AtomicInteger calls = new AtomicInteger();

    CandidateScorecard scorecard = service.compare(List.of(run), routingPolicy, request -> {
      if (calls.incrementAndGet() == 36) {
        throw new IllegalStateException("judge unavailable");
      }
      return new EvaluationResponse(true, 0.90f, "Covered", Map.of(
          "expectedConceptCoverage", 90));
    }).candidates().getFirst();

    assertThat(scorecard.quality().semanticJudgeCoverage()).isLessThan(1.0);
    assertThat(scorecard.quality().qualityStatus()).isEqualTo(QualityStatus.NOT_EVALUATED);
  }

  private CandidateRun passingRun(String candidate, String provider, String model) {
    return passingRunFor(routingPolicy, candidate, provider, model);
  }

  private CandidateRun passingRunFor(
      RoutingPolicy policy, String candidate, String provider, String model) {
    List<SampleResult> samples = new ArrayList<>();
    int duration = 1;
    for (EvaluationCase evaluationCase : dataset.cases()) {
      int rangeMiddle = (evaluationCase.expectations().acceptableScore().minimum()
          + evaluationCase.expectations().acceptableScore().maximum()) / 2;
      for (int repetition = 1; repetition <= 3; repetition++) {
        samples.add(new SampleResult(
            evaluationCase.id(), repetition, SampleStatus.SUCCEEDED,
            new ListingReviewPayload(
                rangeMiddle,
                evaluationCase.expectations().expectedConcepts(),
                List.of(),
                List.of("Add the missing listing details"),
                policy.requiresHumanReview(evaluationCase.id())),
            null,
            duration++));
      }
    }
    EvaluationManifest manifest = new EvaluationManifest(
        1, "listing-quality-v1", candidate, provider, model,
        ModelIdentitySource.RUNTIME_METRICS,
        dataset.manifest().datasetVersion(), dataset.checksum(),
        dataset.manifest().rubricVersion(),
        new SourceRevision("abc", true, "d".repeat(64)),
        new RuntimeEnvironment(provider, "version", "macOS", "aarch64", "Apple M4", 32, "25"),
        new BigDecimal("0.2"), 800, 3,
        Instant.parse("2026-07-14T20:00:00Z"), Instant.parse("2026-07-14T20:10:00Z"),
        100, new TokenUsage(10L, 5L, 15L), new TokenUsage(1000L, 500L, 1500L));
    return new CandidateRun(manifest, samples);
  }

  private SampleResult withReviewDecision(SampleResult sample, boolean requiresReview) {
    ListingReviewPayload review = sample.review();
    return new SampleResult(
        sample.caseId(), sample.repetition(), sample.status(),
        new ListingReviewPayload(
            review.qualityScore(), review.missingFields(), review.issues(), review.suggestions(),
            requiresReview),
        sample.failure(), sample.durationMs());
  }
}
