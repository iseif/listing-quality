package dev.iseif.listingquality.evaluation.report;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ModelPrice(
    String model,
    BigDecimal inputUsdPerMillion,
    BigDecimal outputUsdPerMillion,
    LocalDate verifiedAt,
    String sourceUrl) {}
