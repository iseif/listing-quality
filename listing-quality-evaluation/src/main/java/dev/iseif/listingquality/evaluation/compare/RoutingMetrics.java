package dev.iseif.listingquality.evaluation.compare;

public record RoutingMetrics(
    int truePositiveCount,
    int falseNegativeCount,
    int falsePositiveCount,
    int trueNegativeCount,
    Double precision,
    Double recall,
    Double falsePositiveRate,
    Double accuracy,
    int disagreementCaseCount,
    Double disagreementCaseRate) {}
