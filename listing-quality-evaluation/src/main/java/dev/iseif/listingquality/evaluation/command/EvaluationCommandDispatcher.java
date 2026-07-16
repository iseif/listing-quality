package dev.iseif.listingquality.evaluation.command;

import dev.iseif.listingquality.evaluation.collect.*;
import dev.iseif.listingquality.evaluation.compare.*;
import dev.iseif.listingquality.evaluation.dataset.DatasetLoader;
import dev.iseif.listingquality.evaluation.dataset.EvaluationDataset;
import dev.iseif.listingquality.evaluation.report.JsonReportWriter;
import dev.iseif.listingquality.evaluation.report.MarkdownReportWriter;
import dev.iseif.listingquality.evaluation.report.PriceTable;
import dev.iseif.listingquality.evaluation.result.CandidateRun;
import dev.iseif.listingquality.evaluation.result.ResultStore;
import dev.iseif.listingquality.evaluation.routing.RoutingPolicy;
import dev.iseif.listingquality.evaluation.routing.RoutingPolicyLoader;
import org.springframework.ai.evaluation.Evaluator;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.function.Supplier;

public final class EvaluationCommandDispatcher {

  private static final String PRICE_TABLE = "pricing/model-prices-2026-07-15.json";

  private final ObjectMapper objectMapper;
  private final DatasetLoader datasetLoader;
  private final RestClient.Builder restClientBuilder;
  private final Supplier<Evaluator> evaluatorSupplier;
  private final SourceRevisionProvider sourceRevisionProvider;
  private final RuntimeEnvironmentProvider environmentProvider;
  private final Clock clock;

  public EvaluationCommandDispatcher(
      ObjectMapper objectMapper,
      DatasetLoader datasetLoader,
      RestClient.Builder restClientBuilder,
      Supplier<Evaluator> evaluatorSupplier,
      SourceRevisionProvider sourceRevisionProvider,
      RuntimeEnvironmentProvider environmentProvider,
      Clock clock) {
    this.objectMapper = objectMapper;
    this.datasetLoader = datasetLoader;
    this.restClientBuilder = restClientBuilder;
    this.evaluatorSupplier = evaluatorSupplier;
    this.sourceRevisionProvider = sourceRevisionProvider;
    this.environmentProvider = environmentProvider;
    this.clock = clock;
  }

  public void dispatch(EvaluationCommand command) {
    switch (command) {
      case CollectCommand collect -> collect(collect);
      case CompareCommand compare -> compare(compare);
    }
  }

  private void collect(CollectCommand command) {
    ResultStore resultStore = new ResultStore(objectMapper, command.outputRoot());
    CandidateCollector collector = new CandidateCollector(
        datasetLoader,
        new EvaluationApiClient(
            restClientBuilder.clone(), command.baseUrl(), command.timeout(), Sleeper.threadSleep()),
        new TokenMetricReader(restClientBuilder.clone(), command.baseUrl()),
        resultStore,
        sourceRevisionProvider,
        environmentProvider,
        clock);
    collector.collect(command);
  }

  private void compare(CompareCommand command) {
    ResultStore resultStore = new ResultStore(objectMapper, command.inputRoot());
    List<CandidateRun> runs = resultStore.loadCompleted(command.experiment());
    if (runs.isEmpty()) {
      throw new IllegalStateException(
          "No completed candidate runs found for " + command.experiment());
    }
    EvaluationDataset dataset = datasetLoader.load(
        runs.getFirst().manifest().datasetVersion());
    RoutingPolicyLoader routingPolicyLoader = new RoutingPolicyLoader(objectMapper);
    RoutingPolicy routingPolicy = command.routingPolicy() == null
        ? routingPolicyLoader.fromDataset(dataset)
        : routingPolicyLoader.load(command.routingPolicy(), dataset);
    Evaluator evaluator = command.semantic() ? semanticEvaluator() : null;
    ComparisonService comparisonService = new ComparisonService(
        datasetLoader,
        PriceTable.load(objectMapper, PRICE_TABLE),
        objectMapper,
        new RunCompatibilityValidator());
    List<SampleAssessment> previousAssessments;
    if (command.semanticSource() != null) {
      previousAssessments = new SemanticAssessmentSource(objectMapper).load(
          command.semanticSource(), command.experiment(), dataset, runs);
    } else if (command.resumeSemantic()) {
      previousAssessments = loadPreviousReport(command, runs.getFirst()).assessments();
    } else {
      previousAssessments = List.of();
    }
    ScorecardReport report = comparisonService.compare(
        runs, routingPolicy, evaluator, previousAssessments);
    new JsonReportWriter(objectMapper).write(report, command.outputDirectory());
    new MarkdownReportWriter().write(report, command.outputDirectory());
  }

  private ScorecardReport loadPreviousReport(CompareCommand command, CandidateRun firstRun) {
    Path reportPath = command.outputDirectory().resolve("comparison.json");
    if (!Files.isRegularFile(reportPath)) {
      throw new IllegalStateException(
          "Semantic resume requires an existing comparison report: " + reportPath);
    }
    try {
      ScorecardReport report = objectMapper.readValue(
          Files.readAllBytes(reportPath), ScorecardReport.class);
      if (!command.experiment().equals(report.experiment())) {
        throw new IllegalStateException("Existing comparison report uses another experiment");
      }
      if (!firstRun.manifest().datasetChecksum().equals(report.datasetChecksum())
          || !firstRun.manifest().rubricVersion().equals(report.semanticRubricVersion())) {
        throw new IllegalStateException(
            "Existing comparison report uses another dataset or rubric");
      }
      return report;
    } catch (IOException exception) {
      throw new IllegalStateException("Existing comparison report could not be read", exception);
    }
  }

  private Evaluator semanticEvaluator() {
    Evaluator evaluator = evaluatorSupplier.get();
    if (evaluator == null) {
      throw new IllegalStateException(
          "Semantic comparison requires the judge-openai profile");
    }
    return evaluator;
  }
}
