package dev.iseif.listingquality.evaluation.result;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

public record EvaluationManifest(
    int schemaVersion,
    String experiment,
    String candidate,
    String provider,
    String model,
    ModelIdentitySource modelIdentitySource,
    String datasetVersion,
    String datasetChecksum,
    String rubricVersion,
    SourceRevision sourceRevision,
    RuntimeEnvironment environment,
    BigDecimal temperature,
    int tokenLimit,
    int repetitions,
    Instant startedAt,
    Instant completedAt,
    long warmupDurationMs,
    TokenUsage warmupTokenUsage,
    TokenUsage measuredTokenUsage) {

  private static final Pattern WINDOWS_PATH = Pattern.compile("^[A-Za-z]:[\\\\/].*");

  public EvaluationManifest {
    requireIdentifier("experiment", experiment);
    requireIdentifier("candidate", candidate);
    if (schemaVersion < 1 || tokenLimit < 1 || repetitions < 1 || warmupDurationMs < 0) {
      throw new IllegalArgumentException("Manifest numeric values are invalid");
    }
    if (environment == null || sourceRevision == null || startedAt == null) {
      throw new IllegalArgumentException("Manifest metadata must not be null");
    }
    for (String value : List.of(
        environment.runtime(), environment.runtimeVersion(), environment.operatingSystem(),
        environment.architecture(), environment.processor(), environment.javaVersion())) {
      rejectAbsolutePath(value);
    }
  }

  private static void requireIdentifier(String name, String value) {
    if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
      throw new IllegalArgumentException("Invalid " + name + " identifier");
    }
  }

  private static void rejectAbsolutePath(String value) {
    if (value == null || value.startsWith("/Users/") || value.startsWith("/home/")
        || value.startsWith("/private/") || WINDOWS_PATH.matcher(value).matches()) {
      throw new IllegalArgumentException("Publishable metadata must not contain a local absolute path");
    }
  }
}
