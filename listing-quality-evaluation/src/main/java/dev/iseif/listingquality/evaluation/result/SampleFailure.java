package dev.iseif.listingquality.evaluation.result;

public record SampleFailure(Integer httpStatus, String errorCode, String category) {}
