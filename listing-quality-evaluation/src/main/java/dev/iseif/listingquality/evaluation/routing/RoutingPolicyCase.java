package dev.iseif.listingquality.evaluation.routing;

public record RoutingPolicyCase(
    String caseId,
    boolean requiresHumanReview,
    String reason) {}
