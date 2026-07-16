package dev.iseif.listingquality.evaluation.compare;

import java.math.BigDecimal;

public record CandidateScorecard(
    String candidate,
    String provider,
    String model,
    BigDecimal temperature,
    int sampleCount,
    SafetySummary safety,
    QualitySummary quality,
    OperationalSummary operations) {}
