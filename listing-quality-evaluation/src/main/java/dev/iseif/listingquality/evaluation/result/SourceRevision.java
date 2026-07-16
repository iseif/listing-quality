package dev.iseif.listingquality.evaluation.result;

public record SourceRevision(String commit, boolean dirty, String diffSha256) {}
