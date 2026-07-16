package dev.iseif.listingquality.evaluation.routing;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record RoutingPolicy(
    String policyVersion,
    String checksum,
    Map<String, RoutingPolicyCase> cases) {

  public RoutingPolicy {
    cases = Collections.unmodifiableMap(new LinkedHashMap<>(cases));
  }

  public boolean requiresHumanReview(String caseId) {
    RoutingPolicyCase policyCase = cases.get(caseId);
    if (policyCase == null) {
      throw new IllegalArgumentException("Routing policy has no case: " + caseId);
    }
    return policyCase.requiresHumanReview();
  }
}
