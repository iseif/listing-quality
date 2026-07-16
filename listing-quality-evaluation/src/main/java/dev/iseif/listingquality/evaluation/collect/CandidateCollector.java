package dev.iseif.listingquality.evaluation.collect;

import dev.iseif.listingquality.evaluation.command.CollectCommand;
import dev.iseif.listingquality.evaluation.dataset.DatasetLoader;
import dev.iseif.listingquality.evaluation.dataset.EvaluationCase;
import dev.iseif.listingquality.evaluation.dataset.EvaluationDataset;
import dev.iseif.listingquality.evaluation.result.*;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

public final class CandidateCollector {

  private final DatasetLoader datasetLoader;
  private final ListingQualityClient apiClient;
  private final TokenUsageReader tokenReader;
  private final ResultStore resultStore;
  private final SourceRevisionProvider sourceRevisionProvider;
  private final RuntimeEnvironmentProvider environmentProvider;
  private final Clock clock;

  public CandidateCollector(
      DatasetLoader datasetLoader,
      ListingQualityClient apiClient,
      TokenUsageReader tokenReader,
      ResultStore resultStore,
      SourceRevisionProvider sourceRevisionProvider,
      RuntimeEnvironmentProvider environmentProvider,
      Clock clock) {
    this.datasetLoader = datasetLoader;
    this.apiClient = apiClient;
    this.tokenReader = tokenReader;
    this.resultStore = resultStore;
    this.sourceRevisionProvider = sourceRevisionProvider;
    this.environmentProvider = environmentProvider;
    this.clock = clock;
  }

  public Path collect(CollectCommand command) {
    EvaluationDataset dataset = datasetLoader.load("listing-quality-v1");
    apiClient.awaitReady(5, Duration.ofMillis(500));
    SourceRevision sourceRevision = sourceRevisionProvider.capture();
    RuntimeEnvironment environment = environmentProvider.capture(
        command.runtime(), command.runtimeVersion());
    Instant startedAt = clock.instant();
    EvaluationManifest initial = manifest(
        command, dataset, sourceRevision, environment, ModelIdentitySource.OPERATOR_SUPPLIED,
        startedAt, null, 0, TokenUsage.notReported(), TokenUsage.notReported());
    Path partial = resultStore.begin(initial, command.overwrite());

    TokenMetricSnapshot beforeWarmup = tokenReader.read();
    EvaluationCase warmupCase = dataset.cases().stream()
        .filter(candidate -> candidate.id().equals(dataset.manifest().warmupCaseId()))
        .findFirst()
        .orElseThrow();
    ReviewCallResult warmup = apiClient.review(warmupCase.listing());
    TokenMetricSnapshot measuredBaseline = tokenReader.read();

    for (EvaluationCase evaluationCase : dataset.cases()) {
      for (int repetition = 1; repetition <= command.repetitions(); repetition++) {
        resultStore.append(partial, toSample(
            evaluationCase.id(), repetition, apiClient.review(evaluationCase.listing())));
      }
    }

    TokenMetricSnapshot afterMeasured = tokenReader.read();
    ModelIdentitySource identitySource = command.model().equals(afterMeasured.responseModel())
        ? ModelIdentitySource.RUNTIME_METRICS
        : ModelIdentitySource.OPERATOR_SUPPLIED;
    EvaluationManifest completed = manifest(
        command, dataset, sourceRevision, environment, identitySource,
        startedAt, clock.instant(), warmup.durationMs(),
        measuredBaseline.usage().minus(beforeWarmup.usage()),
        afterMeasured.usage().minus(measuredBaseline.usage()));
    return resultStore.complete(partial, completed);
  }

  private SampleResult toSample(String caseId, int repetition, ReviewCallResult result) {
    return switch (result) {
      case ReviewSuccess success -> new SampleResult(
          caseId, repetition, SampleStatus.SUCCEEDED, success.review(), null,
          success.durationMs());
      case ReviewFailure failure -> new SampleResult(
          caseId, repetition, SampleStatus.FAILED, null,
          new SampleFailure(failure.httpStatus(), failure.errorCode(), failure.category()),
          failure.durationMs());
    };
  }

  private EvaluationManifest manifest(
      CollectCommand command,
      EvaluationDataset dataset,
      SourceRevision sourceRevision,
      RuntimeEnvironment environment,
      ModelIdentitySource identitySource,
      Instant startedAt,
      Instant completedAt,
      long warmupDuration,
      TokenUsage warmupUsage,
      TokenUsage measuredUsage) {
    return new EvaluationManifest(
        1,
        command.experiment(),
        command.candidate(),
        command.provider(),
        command.model(),
        identitySource,
        dataset.manifest().datasetVersion(),
        dataset.checksum(),
        dataset.manifest().rubricVersion(),
        sourceRevision,
        environment,
        command.temperature(),
        command.tokenLimit(),
        command.repetitions(),
        startedAt,
        completedAt,
        warmupDuration,
        warmupUsage,
        measuredUsage);
  }
}
