package dev.iseif.listingquality.evaluation.collect;

public record ReviewFailure(
    Integer httpStatus,
    String errorCode,
    String category,
    long durationMs) implements ReviewCallResult {}
