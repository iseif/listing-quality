package dev.iseif.listingquality.evaluation.collect;

import dev.iseif.listingquality.evaluation.result.TokenUsage;

public record TokenMetricSnapshot(TokenUsage usage, String responseModel) {}
