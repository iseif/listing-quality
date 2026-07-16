package dev.iseif.listingquality.evaluation.compare;

import dev.iseif.listingquality.evaluation.report.CostEstimate;
import dev.iseif.listingquality.evaluation.result.TokenUsage;

public record OperationalSummary(
    int truePositiveCount,
    int falseNegativeCount,
    int falsePositiveCount,
    int trueNegativeCount,
    Double humanReviewPrecision,
    Double humanReviewRecall,
    Double falsePositiveRate,
    Double routingAccuracy,
    int disagreementCaseCount,
    Double disagreementCaseRate,
    long medianLatencyMs,
    long p95LatencyMs,
    TokenUsage tokenUsage,
    CostEstimate cost) {}
