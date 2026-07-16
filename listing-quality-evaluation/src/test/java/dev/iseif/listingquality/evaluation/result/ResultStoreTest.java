package dev.iseif.listingquality.evaluation.result;

import dev.iseif.listingquality.evaluation.api.ListingReviewPayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResultStoreTest {

  @TempDir
  Path temporaryDirectory;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void writesSamplesToPartialRunAndAtomicallyCompletesIt() {
    ResultStore store = new ResultStore(objectMapper, temporaryDirectory);
    EvaluationManifest started = manifest(null);

    Path partial = store.begin(started, false);
    store.append(partial, success("case-1", 1));
    store.append(partial, failure("case-1", 2));
    Path completed = store.complete(partial, manifest(Instant.parse("2026-07-14T20:00:00Z")));

    assertThat(partial).doesNotExist();
    assertThat(completed).exists().isDirectory();
    CandidateRun run = store.load(completed);
    assertThat(run.manifest().completedAt()).isEqualTo(Instant.parse("2026-07-14T20:00:00Z"));
    assertThat(run.samples()).containsExactly(success("case-1", 1), failure("case-1", 2));
  }

  @Test
  void refusesToOverwriteACompletedRunByDefault() {
    ResultStore store = new ResultStore(objectMapper, temporaryDirectory);
    Path first = store.begin(manifest(null), false);
    store.complete(first, manifest(Instant.parse("2026-07-14T20:00:00Z")));

    assertThatThrownBy(() -> store.begin(manifest(null), false))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Completed candidate run already exists: candidate-a");
  }

  @Test
  void explicitOverwriteReplacesCompletedAndPartialRuns() {
    ResultStore store = new ResultStore(objectMapper, temporaryDirectory);
    Path first = store.begin(manifest(null), false);
    store.append(first, success("old", 1));
    store.complete(first, manifest(Instant.parse("2026-07-14T20:00:00Z")));

    Path replacement = store.begin(manifest(null), true);
    store.append(replacement, success("new", 1));
    Path completed = store.complete(
        replacement, manifest(Instant.parse("2026-07-14T20:10:00Z")));

    assertThat(store.load(completed).samples()).extracting(SampleResult::caseId)
        .containsExactly("new");
  }

  @Test
  void comparisonDiscoveryIgnoresPartialRuns() {
    ResultStore store = new ResultStore(objectMapper, temporaryDirectory);
    store.begin(manifest(null), false);

    assertThat(store.loadCompleted("experiment-a")).isEmpty();
  }

  @Test
  void enforcesSuccessAndFailureSampleInvariants() {
    assertThatThrownBy(() -> new SampleResult(
        "case", 1, SampleStatus.SUCCEEDED, null, null, 10))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Successful sample requires a review and no failure");
    assertThatThrownBy(() -> new SampleResult(
        "case", 1, SampleStatus.FAILED,
        new ListingReviewPayload(50, List.of(), List.of(), List.of(), false),
        new SampleFailure(502, "AI_RESPONSE_INVALID", "HTTP"), 10))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Failed sample requires a failure and no review");
  }

  @Test
  void rejectsLocalAbsolutePathsInPublishableMetadata() {
    RuntimeEnvironment unsafeEnvironment = new RuntimeEnvironment(
        "omlx", "/Users/example/venv/bin/omlx", "macOS", "aarch64",
        "Apple M4", 32_000_000_000L, "25");

    assertThatThrownBy(() -> manifest(null, unsafeEnvironment))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not contain a local absolute path");
  }

  private EvaluationManifest manifest(Instant completedAt) {
    return manifest(completedAt, new RuntimeEnvironment(
        "openai-api", "2026-07-14", "macOS", "aarch64", "Apple M4",
        32_000_000_000L, "25"));
  }

  private EvaluationManifest manifest(Instant completedAt, RuntimeEnvironment environment) {
    return new EvaluationManifest(
        1, "experiment-a", "candidate-a", "openai", "gpt-5.6",
        ModelIdentitySource.OPERATOR_SUPPLIED,
        "listing-quality-v1", "a".repeat(64), "listing-quality-rubric-v1",
        new SourceRevision("abc123", true, "b".repeat(64)), environment,
        new BigDecimal("0.2"), 800, 3,
        Instant.parse("2026-07-14T19:00:00Z"), completedAt,
        123, new TokenUsage(10L, 5L, 15L), new TokenUsage(100L, 50L, 150L));
  }

  private SampleResult success(String caseId, int repetition) {
    return new SampleResult(caseId, repetition, SampleStatus.SUCCEEDED,
        new ListingReviewPayload(70, List.of("isbn"), List.of(), List.of("Add ISBN"), false),
        null, 42);
  }

  private SampleResult failure(String caseId, int repetition) {
    return new SampleResult(caseId, repetition, SampleStatus.FAILED, null,
        new SampleFailure(503, "AI_PROVIDER_UNAVAILABLE", "HTTP"), 51);
  }
}
