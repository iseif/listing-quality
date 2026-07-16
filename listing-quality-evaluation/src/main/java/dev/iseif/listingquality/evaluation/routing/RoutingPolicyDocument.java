package dev.iseif.listingquality.evaluation.routing;

import java.util.List;

public record RoutingPolicyDocument(
    int schemaVersion,
    String policyVersion,
    List<RoutingPolicyCase> cases) {

  public RoutingPolicyDocument {
    cases = cases == null ? List.of() : List.copyOf(cases);
  }
}
