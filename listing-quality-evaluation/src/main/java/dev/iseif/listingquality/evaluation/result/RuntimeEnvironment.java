package dev.iseif.listingquality.evaluation.result;

public record RuntimeEnvironment(
    String runtime,
    String runtimeVersion,
    String operatingSystem,
    String architecture,
    String processor,
    long memoryBytes,
    String javaVersion) {}
