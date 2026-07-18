package dev.iseif.listingquality.enrichment.service.execution;

import java.time.Duration;
import java.util.Objects;

/**
 * How aggressively one provider route retries and when it stops being called at all.
 *
 * <p>These knobs are tuned together, so they travel together. The per-route timeout is not part
 * of it: primary and fallback deliberately get different deadlines from the same policy.
 */
public record RoutePolicy(
    int maxAttempts,
    Duration retryWait,
    int circuitWindow,
    float circuitFailureThreshold,
    Duration circuitOpenDuration,
    int halfOpenCalls) {

  public RoutePolicy {
    Objects.requireNonNull(retryWait, "retryWait");
    Objects.requireNonNull(circuitOpenDuration, "circuitOpenDuration");
    if (maxAttempts < 1) {
      throw new IllegalArgumentException("maxAttempts must be at least one");
    }
    if (circuitWindow < 1) {
      throw new IllegalArgumentException("circuitWindow must be at least one");
    }
    if (halfOpenCalls < 1) {
      throw new IllegalArgumentException("halfOpenCalls must be at least one");
    }
  }
}
