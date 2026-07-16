package dev.iseif.listingquality.evaluation.compare;

import dev.iseif.listingquality.evaluation.result.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RunCompatibilityValidatorTest {

  private final RunCompatibilityValidator validator = new RunCompatibilityValidator();

  @Test
  void acceptsDifferentCandidateModelRuntimeTimeHardwareAndTemperature() {
    CandidateRun first = run(manifest("candidate-a", "openai", "gpt-5.6",
        "a".repeat(64), source("abc", true, "b".repeat(64)), new BigDecimal("0.2"), 800, 3));
    CandidateRun second = run(new EvaluationManifest(
        1, "experiment", "candidate-b", "ollama", "gemma4:e4b",
        ModelIdentitySource.RUNTIME_METRICS, "listing-quality-v1", "a".repeat(64), "rubric-v1",
        source("abc", true, "b".repeat(64)),
        new RuntimeEnvironment("ollama", "0.9", "Linux", "x86_64", "CPU", 16, "25"),
        new BigDecimal("1.0"), 800, 3, Instant.parse("2026-07-14T21:00:00Z"),
        Instant.parse("2026-07-14T21:10:00Z"), 50,
        TokenUsage.notReported(), TokenUsage.notReported()));

    assertThatCode(() -> validator.validate(List.of(first, second))).doesNotThrowAnyException();
  }

  @Test
  void rejectsDifferentDatasetChecksum() {
    assertMismatch(
        manifest("candidate-a", "openai", "gpt-5.6", "a".repeat(64),
            source("abc", false, "b".repeat(64)), new BigDecimal("0.2"), 800, 3),
        manifest("candidate-b", "gemini", "gemini-3.5-flash", "c".repeat(64),
            source("abc", false, "b".repeat(64)), new BigDecimal("0.2"), 800, 3),
        "datasetChecksum");
  }

  @Test
  void rejectsDifferentSourceRevisionOrDirtyFingerprint() {
    assertMismatch(
        manifest("candidate-a", "openai", "gpt-5.6", "a".repeat(64),
            source("abc", true, "b".repeat(64)), new BigDecimal("0.2"), 800, 3),
        manifest("candidate-b", "gemini", "gemini-3.5-flash", "a".repeat(64),
            source("def", true, "d".repeat(64)), new BigDecimal("0.2"), 800, 3),
        "sourceRevision");
  }

  @Test
  void rejectsDifferentTokenLimitOrRepetitionCount() {
    EvaluationManifest baseline = manifest("candidate-a", "openai", "gpt-5.6",
        "a".repeat(64), source("abc", false, "b".repeat(64)),
        new BigDecimal("0.2"), 800, 3);
    assertMismatch(baseline, manifest("candidate-b", "gemini", "gemini-3.5-flash",
        "a".repeat(64), source("abc", false, "b".repeat(64)),
        new BigDecimal("0.2"), 512, 3), "tokenLimit");
    assertMismatch(baseline, manifest("candidate-b", "gemini", "gemini-3.5-flash",
        "a".repeat(64), source("abc", false, "b".repeat(64)),
        new BigDecimal("0.2"), 800, 2), "repetitions");
  }

  private void assertMismatch(
      EvaluationManifest first, EvaluationManifest second, String field) {
    assertThatThrownBy(() -> validator.validate(List.of(run(first), run(second))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(field)
        .hasMessageContaining("candidate-a", "candidate-b");
  }

  private CandidateRun run(EvaluationManifest manifest) {
    return new CandidateRun(manifest, List.of());
  }

  private SourceRevision source(String commit, boolean dirty, String hash) {
    return new SourceRevision(commit, dirty, hash);
  }

  private EvaluationManifest manifest(
      String candidate, String provider, String model, String checksum,
      SourceRevision revision, BigDecimal temperature, int tokenLimit, int repetitions) {
    return new EvaluationManifest(
        1, "experiment", candidate, provider, model, ModelIdentitySource.OPERATOR_SUPPLIED,
        "listing-quality-v1", checksum, "rubric-v1", revision,
        new RuntimeEnvironment("runtime", "version", "macOS", "aarch64", "CPU", 16, "25"),
        temperature, tokenLimit, repetitions, Instant.parse("2026-07-14T20:00:00Z"),
        Instant.parse("2026-07-14T20:10:00Z"), 50,
        TokenUsage.notReported(), TokenUsage.notReported());
  }
}
