package dev.iseif.listingquality.evaluation.command;

import java.nio.file.Path;

public record CompareCommand(
    String experiment,
    Path inputRoot,
    Path outputDirectory,
    String routingPolicy,
    Path semanticSource,
    boolean semantic,
    boolean resumeSemantic) implements EvaluationCommand {}
