package dev.iseif.listingquality.evaluation.command;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;

public record CollectCommand(
    String experiment,
    String candidate,
    String provider,
    String model,
    String runtime,
    String runtimeVersion,
    BigDecimal temperature,
    int tokenLimit,
    URI baseUrl,
    int repetitions,
    Duration timeout,
    Path outputRoot,
    boolean overwrite) implements EvaluationCommand {}
