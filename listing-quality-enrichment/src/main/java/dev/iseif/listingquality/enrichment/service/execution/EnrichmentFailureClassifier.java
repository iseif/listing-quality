package dev.iseif.listingquality.enrichment.service.execution;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.TimeoutException;

public final class EnrichmentFailureClassifier {

  private static final Set<ModelFailureCategory> RETRYABLE_CATEGORIES = EnumSet.of(
      ModelFailureCategory.PROVIDER_UNAVAILABLE,
      ModelFailureCategory.RATE_LIMITED,
      ModelFailureCategory.QUOTA_EXHAUSTED);

  public boolean isFallbackEligible(Throwable failure) {
    return failure instanceof ModelExecutionException modelFailure && modelFailure.eligible()
        || failure instanceof TimeoutException
        || failure instanceof CallNotPermittedException;
  }

  public boolean isRetryable(Throwable failure) {
    return failure instanceof ModelExecutionException modelFailure
        && modelFailure.eligible()
        && RETRYABLE_CATEGORIES.contains(modelFailure.category());
  }

  public boolean recordsCircuitFailure(Throwable failure) {
    return isRetryable(failure)
        || failure instanceof TimeoutException
        || failure instanceof ModelExecutionException modelFailure
            && modelFailure.category() == ModelFailureCategory.INVALID_MODEL_OUTPUT;
  }
}
