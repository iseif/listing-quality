package dev.iseif.listingquality.evaluation.compare;

import dev.iseif.listingquality.evaluation.dataset.DatasetLoader;
import dev.iseif.listingquality.evaluation.dataset.EvaluationCase;
import dev.iseif.listingquality.evaluation.dataset.EvaluationDataset;
import dev.iseif.listingquality.evaluation.grade.ContractGrader;
import dev.iseif.listingquality.evaluation.grade.ForbiddenClaimGrader;
import dev.iseif.listingquality.evaluation.grade.HumanReviewGrader;
import dev.iseif.listingquality.evaluation.grade.HumanReviewOutcome;
import dev.iseif.listingquality.evaluation.report.CostEstimate;
import dev.iseif.listingquality.evaluation.report.CostStatus;
import dev.iseif.listingquality.evaluation.report.ModelPrice;
import dev.iseif.listingquality.evaluation.report.PriceTable;
import dev.iseif.listingquality.evaluation.result.CandidateRun;
import dev.iseif.listingquality.evaluation.result.SampleResult;
import dev.iseif.listingquality.evaluation.result.TokenUsage;
import dev.iseif.listingquality.evaluation.routing.RoutingPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.ai.evaluation.Evaluator;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ComparisonService {

  private static final Logger logger = LoggerFactory.getLogger(ComparisonService.class);

  private final DatasetLoader datasetLoader;
  private final PriceTable prices;
  private final ObjectMapper objectMapper;
  private final RunCompatibilityValidator compatibilityValidator;
  private final ContractGrader contractGrader = new ContractGrader();
  private final ForbiddenClaimGrader forbiddenGrader = new ForbiddenClaimGrader();
  private final HumanReviewGrader humanReviewGrader = new HumanReviewGrader();
  private final RoutingMetricsCalculator routingMetricsCalculator = new RoutingMetricsCalculator();

  public ComparisonService(
      DatasetLoader datasetLoader,
      PriceTable prices,
      ObjectMapper objectMapper,
      RunCompatibilityValidator compatibilityValidator) {
    this.datasetLoader = datasetLoader;
    this.prices = prices;
    this.objectMapper = objectMapper;
    this.compatibilityValidator = compatibilityValidator;
  }

  public ScorecardReport compare(
      List<CandidateRun> runs,
      RoutingPolicy routingPolicy,
      Evaluator evaluator) {
    return compare(runs, routingPolicy, evaluator, List.of());
  }

  public ScorecardReport compare(
      List<CandidateRun> runs,
      RoutingPolicy routingPolicy,
      Evaluator evaluator,
      List<SampleAssessment> reusableAssessments) {
    compatibilityValidator.validate(runs);
    EvaluationDataset dataset = datasetLoader.load(runs.getFirst().manifest().datasetVersion());
    Map<String, EvaluationCase> cases = dataset.cases().stream()
        .collect(Collectors.toMap(EvaluationCase::id, Function.identity(), (left, right) -> left,
            LinkedHashMap::new));
    Map<AssessmentKey, SampleAssessment> reusable = reusableAssessments(reusableAssessments);
    List<CandidateScorecard> scorecards = new ArrayList<>();
    List<SampleAssessment> assessments = new ArrayList<>();
    for (CandidateRun run : runs) {
      validateMatrix(run, dataset);
      scorecards.add(summarizeScorecard(
          run, cases, routingPolicy, evaluator, reusable, assessments));
    }
    return new ScorecardReport(
        2,
        runs.getFirst().manifest().experiment(),
        dataset.manifest().datasetVersion(),
        dataset.checksum(),
        dataset.manifest().rubricVersion(),
        routingPolicy.policyVersion(),
        routingPolicy.checksum(),
        scorecards,
        assessments,
        List.of(
            "The dataset is intentionally small and task specific.",
            "Semantic quality reuses v1 assessments from one judge model and requires human audit.",
            "The v1 judge context contained the previous routing expectation, which may have influenced expected-concept coverage even though routing correctness was not scored.",
            "Local latency depends on the recorded hardware and runtime.",
            "Cloud prices are estimates from a dated price table.",
            "Model and hosted-service behavior can drift after collection."));
  }

  private CandidateScorecard summarizeScorecard(
      CandidateRun run,
      Map<String, EvaluationCase> cases,
      RoutingPolicy routingPolicy,
      Evaluator evaluator,
      Map<AssessmentKey, SampleAssessment> reusableAssessments,
      List<SampleAssessment> allAssessments) {
    int valid = 0;
    int forbidden = 0;
    int semanticEvaluated = 0;
    int semanticCoverageTotal = 0;
    List<Long> durations = new ArrayList<>();

    for (SampleResult sample : run.samples()) {
      EvaluationCase evaluationCase = cases.get(sample.caseId());
      boolean validResponse = contractGrader.isValid(sample);
      boolean scoreInRange = contractGrader.scoreInRange(
          sample, evaluationCase.expectations().acceptableScore());
      List<String> matched = forbiddenGrader.matches(
          sample, evaluationCase.expectations().forbiddenClaims());
      HumanReviewOutcome humanOutcome = humanReviewGrader.classify(
          routingPolicy.requiresHumanReview(sample.caseId()), sample);
      if (validResponse) {
        valid++;
      }
      forbidden += matched.size();
      durations.add(sample.durationMs());

      AssessmentKey key = new AssessmentKey(
          run.manifest().candidate(), sample.caseId(), sample.repetition());
      SemanticOutcome semantic = semanticOutcome(reusableAssessments.get(key));
      if (semantic == null) {
        semantic = evaluateSemantic(run, evaluationCase, sample, evaluator);
      }
      if (semantic.status() == SemanticStatus.EVALUATED) {
        semanticEvaluated++;
        semanticCoverageTotal += semantic.coverage();
      }
      allAssessments.add(new SampleAssessment(
          run.manifest().candidate(), sample.caseId(), sample.repetition(), validResponse,
          scoreInRange, humanOutcome, matched, semantic.status(), semantic.coverage(),
          semantic.feedback()));
    }

    double validRate = ratio(valid, run.samples().size());
    double judgeCoverage = ratio(semanticEvaluated, run.samples().size());
    Double expectedCoverage = semanticEvaluated == 0
        ? null
        : semanticCoverageTotal / (double) semanticEvaluated;
    RoutingMetrics routing = routingMetricsCalculator.calculate(run.samples(), routingPolicy);
    SafetySummary safety = SafetySummary.from(
        validRate, routing.recall(), routing.falseNegativeCount(), forbidden);
    QualitySummary quality = QualitySummary.from(expectedCoverage, judgeCoverage);
    OperationalSummary operations = new OperationalSummary(
        routing.truePositiveCount(),
        routing.falseNegativeCount(),
        routing.falsePositiveCount(),
        routing.trueNegativeCount(),
        routing.precision(),
        routing.recall(),
        routing.falsePositiveRate(),
        routing.accuracy(),
        routing.disagreementCaseCount(),
        routing.disagreementCaseRate(),
        Percentiles.median(durations),
        Percentiles.nearestRank(durations, 0.95),
        run.manifest().measuredTokenUsage(),
        cost(run));
    return new CandidateScorecard(
        run.manifest().candidate(),
        run.manifest().provider(),
        run.manifest().model(),
        run.manifest().temperature(),
        run.samples().size(),
        safety,
        quality,
        operations);
  }

  private Map<AssessmentKey, SampleAssessment> reusableAssessments(
      List<SampleAssessment> assessments) {
    Map<AssessmentKey, SampleAssessment> reusable = new LinkedHashMap<>();
    for (SampleAssessment assessment : assessments) {
      AssessmentKey key = new AssessmentKey(
          assessment.candidate(), assessment.caseId(), assessment.repetition());
      if (reusable.putIfAbsent(key, assessment) != null) {
        throw new IllegalArgumentException("Duplicate reusable semantic assessment key");
      }
    }
    return reusable;
  }

  private SemanticOutcome evaluateSemantic(
      CandidateRun run, EvaluationCase evaluationCase, SampleResult sample, Evaluator evaluator) {
    if (evaluator == null || !contractGrader.isValid(sample)) {
      return SemanticOutcome.notEvaluated();
    }
    try {
      String context = objectMapper.writeValueAsString(Map.of(
          "candidate", "candidate-1",
          "caseId", evaluationCase.id(),
          "listing", evaluationCase.listing(),
          "expectations", evaluationCase.expectations()));
      String response = objectMapper.writeValueAsString(sample.review());
      EvaluationResponse result = evaluator.evaluate(new EvaluationRequest(context, response));
      Object coverage = result.getMetadata().get("expectedConceptCoverage");
      if (!(coverage instanceof Number number)) {
        return SemanticOutcome.notEvaluated();
      }
      return new SemanticOutcome(
          SemanticStatus.EVALUATED, number.intValue(), result.getFeedback());
    } catch (RuntimeException exception) {
      logger.warn("Semantic judge failed for candidate={}, case={}, repetition={}, type={}",
          run.manifest().candidate(), evaluationCase.id(), sample.repetition(),
          exception.getClass().getSimpleName());
      return SemanticOutcome.notEvaluated();
    }
  }

  private SemanticOutcome semanticOutcome(SampleAssessment assessment) {
    if (assessment == null || assessment.expectedConceptCoverage() == null) {
      return null;
    }
    return new SemanticOutcome(
        SemanticStatus.EVALUATED,
        assessment.expectedConceptCoverage(),
        assessment.semanticFeedback());
  }

  private CostEstimate cost(CandidateRun run) {
    var price = prices.find(run.manifest().model());
    if (price.isEmpty()) {
      return new CostEstimate(CostStatus.NOT_APPLICABLE, null);
    }
    TokenUsage usage = run.manifest().measuredTokenUsage();
    if (usage.inputTokens() == null || usage.outputTokens() == null) {
      return new CostEstimate(CostStatus.NOT_REPORTED, null);
    }
    ModelPrice modelPrice = price.orElseThrow();
    BigDecimal million = BigDecimal.valueOf(1_000_000L);
    BigDecimal input = BigDecimal.valueOf(usage.inputTokens())
        .multiply(modelPrice.inputUsdPerMillion()).divide(million, 12, RoundingMode.HALF_UP);
    BigDecimal output = BigDecimal.valueOf(usage.outputTokens())
        .multiply(modelPrice.outputUsdPerMillion()).divide(million, 12, RoundingMode.HALF_UP);
    return new CostEstimate(CostStatus.ESTIMATED,
        input.add(output).setScale(6, RoundingMode.HALF_UP));
  }

  private void validateMatrix(CandidateRun run, EvaluationDataset dataset) {
    Map<String, Long> counts = run.samples().stream()
        .collect(Collectors.groupingBy(SampleResult::caseId, Collectors.counting()));
    for (EvaluationCase evaluationCase : dataset.cases()) {
      long count = counts.getOrDefault(evaluationCase.id(), 0L);
      if (count != run.manifest().repetitions()) {
        throw new IllegalArgumentException(
            "Candidate %s has %d samples for %s, expected %d".formatted(
                run.manifest().candidate(), count, evaluationCase.id(),
                run.manifest().repetitions()));
      }
    }
    if (counts.size() != dataset.cases().size()) {
      throw new IllegalArgumentException(
          "Candidate %s contains unknown case IDs".formatted(run.manifest().candidate()));
    }
  }

  private double ratio(int numerator, int denominator) {
    return denominator == 0 ? 1.0 : numerator / (double) denominator;
  }

  private record SemanticOutcome(
      SemanticStatus status, Integer coverage, String feedback) {
    private static SemanticOutcome notEvaluated() {
      return new SemanticOutcome(SemanticStatus.NOT_EVALUATED, null, null);
    }
  }

  private record AssessmentKey(String candidate, String caseId, int repetition) {}
}
