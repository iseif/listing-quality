package dev.iseif.listingquality.evaluation.collect;

import dev.iseif.listingquality.evaluation.api.ListingDraftPayload;
import dev.iseif.listingquality.evaluation.api.ListingReviewPayload;
import dev.iseif.listingquality.evaluation.command.CollectCommand;
import dev.iseif.listingquality.evaluation.dataset.DatasetLoader;
import dev.iseif.listingquality.evaluation.result.*;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateCollectorTest {

  @TempDir
  Path temporaryDirectory;

  @Test
  void collectsWarmupAndThirtySixMeasuredSamplesInDeterministicOrder() {
    ObjectMapper objectMapper = new ObjectMapper();
    FakeClient client = new FakeClient();
    client.results.add(success(11));
    for (int index = 0; index < 36; index++) {
      client.results.add(index == 4
          ? new ReviewFailure(503, "AI_PROVIDER_UNAVAILABLE", "HTTP", 20)
          : success(20 + index));
    }
    FakeMetrics metrics = new FakeMetrics(
        new TokenMetricSnapshot(new TokenUsage(0L, 0L, 0L), "gpt-5.6"),
        new TokenMetricSnapshot(new TokenUsage(10L, 5L, 15L), "gpt-5.6"),
        new TokenMetricSnapshot(new TokenUsage(110L, 55L, 165L), "gpt-5.6"));
    ResultStore store = new ResultStore(objectMapper, temporaryDirectory);
    CandidateCollector collector = new CandidateCollector(
        new DatasetLoader(objectMapper,
            Validation.buildDefaultValidatorFactory().getValidator()),
        client,
        metrics,
        store,
        () -> new SourceRevision("abc123", true, "d".repeat(64)),
        (runtime, version) -> new RuntimeEnvironment(
            runtime, version, "macOS", "aarch64", "Apple M4", 32_000_000_000L, "25"),
        Clock.fixed(Instant.parse("2026-07-14T20:00:00Z"), ZoneOffset.UTC));

    Path completed = collector.collect(command());

    CandidateRun run = store.load(completed);
    assertThat(client.readinessChecks).isEqualTo(1);
    assertThat(client.listings).hasSize(37);
    assertThat(client.listings.getFirst().title()).isEqualTo("Complete running shoes");
    assertThat(run.samples()).hasSize(36);
    assertThat(run.samples()).extracting(sample -> sample.caseId() + ":" + sample.repetition())
        .startsWith("complete-running-shoes:1", "complete-running-shoes:2",
            "complete-running-shoes:3", "book-missing-author-isbn:1");
    assertThat(run.samples()).filteredOn(sample -> sample.status() == SampleStatus.FAILED)
        .singleElement()
        .satisfies(sample -> assertThat(sample.failure().errorCode())
            .isEqualTo("AI_PROVIDER_UNAVAILABLE"));
    assertThat(run.manifest().warmupDurationMs()).isEqualTo(11);
    assertThat(run.manifest().warmupTokenUsage()).isEqualTo(new TokenUsage(10L, 5L, 15L));
    assertThat(run.manifest().measuredTokenUsage()).isEqualTo(new TokenUsage(100L, 50L, 150L));
    assertThat(run.manifest().modelIdentitySource()).isEqualTo(ModelIdentitySource.RUNTIME_METRICS);
  }

  private CollectCommand command() {
    return new CollectCommand(
        "listing-quality-v1", "openai-gpt-5.6", "openai", "gpt-5.6",
        "openai-api", "2026-07-14", new BigDecimal("0.2"), 800,
        URI.create("http://localhost:8080"), 3, Duration.ofMinutes(2),
        temporaryDirectory, false);
  }

  private ReviewSuccess success(long duration) {
    return new ReviewSuccess(
        new ListingReviewPayload(70, List.of(), List.of(), List.of("Improve title"), false),
        duration);
  }

  private static final class FakeClient implements ListingQualityClient {
    private final ArrayDeque<ReviewCallResult> results = new ArrayDeque<>();
    private final List<ListingDraftPayload> listings = new ArrayList<>();
    private int readinessChecks;

    @Override
    public void awaitReady(int attempts, Duration delay) {
      readinessChecks++;
    }

    @Override
    public ReviewCallResult review(ListingDraftPayload listing) {
      listings.add(listing);
      return results.removeFirst();
    }
  }

  private static final class FakeMetrics implements TokenUsageReader {
    private final ArrayDeque<TokenMetricSnapshot> snapshots;

    private FakeMetrics(TokenMetricSnapshot... snapshots) {
      this.snapshots = new ArrayDeque<>(List.of(snapshots));
    }

    @Override
    public TokenMetricSnapshot read() {
      return snapshots.removeFirst();
    }
  }
}
