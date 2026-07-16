package dev.iseif.listingquality.evaluation.dataset;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ScoreRange(
    @Min(0) @Max(100) int minimum,
    @Min(0) @Max(100) int maximum) {}
