package dev.iseif.listingquality.enrichment.service.execution;

import dev.iseif.listingquality.enrichment.config.EnrichmentProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedRetryMetrics;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

public final class ModelRouteFactory {

  private final ExecutorService executor;
  private final RetryRegistry retryRegistry;
  private final CircuitBreakerRegistry circuitBreakerRegistry;

  public ModelRouteFactory(
      EnrichmentProperties.Resilience resilience,
      EnrichmentFailureClassifier classifier,
      ExecutorService executor,
      MeterRegistry meterRegistry) {
    Objects.requireNonNull(resilience);
    Objects.requireNonNull(classifier);
    this.executor = Objects.requireNonNull(executor);
    RetryConfig retryConfig = RetryConfig.custom()
        .maxAttempts(resilience.maxAttempts())
        .waitDuration(resilience.retryWait())
        .retryOnException(classifier::isRetryable)
        .build();
    CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
        .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
        .slidingWindowSize(resilience.circuitWindow())
        .minimumNumberOfCalls(resilience.circuitWindow())
        .failureRateThreshold(resilience.circuitFailureThreshold())
        .waitDurationInOpenState(resilience.circuitOpenDuration())
        .permittedNumberOfCallsInHalfOpenState(resilience.halfOpenCalls())
        .recordException(classifier::recordsCircuitFailure)
        .build();
    this.retryRegistry = RetryRegistry.of(retryConfig);
    this.circuitBreakerRegistry = CircuitBreakerRegistry.of(circuitBreakerConfig);
    TaggedRetryMetrics.ofRetryRegistry(retryRegistry)
        .bindTo(Objects.requireNonNull(meterRegistry));
    TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(circuitBreakerRegistry)
        .bindTo(meterRegistry);
  }

  public ModelRoute create(String routeId, String providerId, Duration timeout) {
    return new ModelRoute(
        providerId,
        timeout,
        retryRegistry.retry(routeId),
        circuitBreakerRegistry.circuitBreaker(routeId),
        executor);
  }
}
