package dev.iseif.listingquality.enrichment.service.execution;

import dev.iseif.listingquality.enrichment.config.EnrichmentProperties;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

public final class ModelRouteFactory {

  private final EnrichmentProperties.Resilience resilience;
  private final EnrichmentFailureClassifier classifier;
  private final ExecutorService executor;

  public ModelRouteFactory(
      EnrichmentProperties.Resilience resilience,
      EnrichmentFailureClassifier classifier,
      ExecutorService executor) {
    this.resilience = Objects.requireNonNull(resilience);
    this.classifier = Objects.requireNonNull(classifier);
    this.executor = Objects.requireNonNull(executor);
  }

  public ModelRoute create(String routeId, String providerId, Duration timeout) {
    RoutePolicy policy = new RoutePolicy(
        resilience.maxAttempts(),
        resilience.retryWait(),
        resilience.circuitWindow(),
        resilience.circuitFailureThreshold(),
        resilience.circuitOpenDuration(),
        resilience.halfOpenCalls());
    return new ModelRoute(routeId, providerId, timeout, policy, classifier, executor);
  }
}
