package dev.iseif.listingquality.evaluation.report;

import java.math.BigDecimal;

public record CostEstimate(CostStatus status, BigDecimal usd) {}
