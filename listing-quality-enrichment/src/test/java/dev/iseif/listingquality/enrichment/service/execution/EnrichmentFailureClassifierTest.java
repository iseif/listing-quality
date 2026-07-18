package dev.iseif.listingquality.enrichment.service.execution;

import dev.iseif.listingquality.enrichment.service.book.exception.InvalidBookEnrichmentResponseException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class EnrichmentFailureClassifierTest {

  private final EnrichmentFailureClassifier classifier = new EnrichmentFailureClassifier();

  @Test
  void allowsFallbackForAvailabilityInvalidOutputTimeoutAndOpenCircuit() {
    assertThat(classifier.isFallbackEligible(eligible(ModelFailureCategory.PROVIDER_UNAVAILABLE)))
        .isTrue();
    assertThat(classifier.isFallbackEligible(eligible(ModelFailureCategory.RATE_LIMITED)))
        .isTrue();
    assertThat(classifier.isFallbackEligible(eligible(ModelFailureCategory.QUOTA_EXHAUSTED)))
        .isTrue();
    assertThat(classifier.isFallbackEligible(eligible(ModelFailureCategory.INVALID_MODEL_OUTPUT)))
        .isTrue();
    assertThat(classifier.isFallbackEligible(new InvalidBookEnrichmentResponseException())).isFalse();
    assertThat(classifier.isFallbackEligible(new TimeoutException())).isTrue();

    CircuitBreaker breaker = CircuitBreaker.ofDefaults("test");
    breaker.transitionToOpenState();
    assertThat(classifier.isFallbackEligible(
        CallNotPermittedException.createCallNotPermittedException(breaker))).isTrue();
  }

  @Test
  void rejectsFallbackForAuthenticationConfigurationSafetyAndUnknownFailures() {
    assertThat(classifier.isFallbackEligible(ineligible(ModelFailureCategory.AUTHENTICATION_FAILED)))
        .isFalse();
    assertThat(classifier.isFallbackEligible(ineligible(ModelFailureCategory.CONFIGURATION_ERROR)))
        .isFalse();
    assertThat(classifier.isFallbackEligible(ineligible(ModelFailureCategory.SAFETY_REFUSAL)))
        .isFalse();
    assertThat(classifier.isFallbackEligible(new IllegalArgumentException())).isFalse();
  }

  @Test
  void retriesOnlyTransportAndCapacityFailures() {
    assertThat(classifier.isRetryable(eligible(ModelFailureCategory.PROVIDER_UNAVAILABLE))).isTrue();
    assertThat(classifier.isRetryable(eligible(ModelFailureCategory.RATE_LIMITED))).isTrue();
    assertThat(classifier.isRetryable(eligible(ModelFailureCategory.QUOTA_EXHAUSTED))).isTrue();
    assertThat(classifier.isRetryable(eligible(ModelFailureCategory.INVALID_MODEL_OUTPUT))).isFalse();
    assertThat(classifier.isRetryable(new InvalidBookEnrichmentResponseException())).isFalse();
  }

  private ModelExecutionException eligible(ModelFailureCategory category) {
    return ModelExecutionException.eligible("provider", category, new RuntimeException("detail"));
  }

  private ModelExecutionException ineligible(ModelFailureCategory category) {
    return ModelExecutionException.ineligible("provider", category, new RuntimeException("detail"));
  }
}
