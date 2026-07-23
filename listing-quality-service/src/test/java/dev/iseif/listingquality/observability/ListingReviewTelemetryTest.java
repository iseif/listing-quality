package dev.iseif.listingquality.observability;

import dev.iseif.listingquality.model.ListingReview;
import dev.iseif.listingquality.service.exception.AiProviderException;
import dev.iseif.listingquality.service.exception.AiProviderUnavailableException;
import dev.iseif.listingquality.service.exception.InvalidAiResponseException;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ListingReviewTelemetryTest {

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final ObservationRegistry observationRegistry = ObservationRegistry.create();
  private ListingReviewTelemetry telemetry;

  @BeforeEach
  void setUp() {
    observationRegistry.observationConfig()
        .observationHandler(new DefaultMeterObservationHandler(meterRegistry));
    telemetry = new ListingReviewTelemetry(observationRegistry);
  }

  @Test
  void recordsOneSuccessfulReview() {
    ListingReview review = new ListingReview(80, List.of(), List.of(), List.of(), false);

    assertThat(telemetry.observe(() -> review)).isSameAs(review);

    assertTimer("success", "reviewed", 1);
  }

  @Test
  void recordsProviderUnavailableWithoutChangingTheException() {
    var failure = new AiProviderUnavailableException(
        new RuntimeException("seller title must never become a tag"));

    assertThatThrownBy(() -> telemetry.observe(() -> {
      throw failure;
    })).isSameAs(failure);

    assertTimer("failure", "provider_unavailable", 1);
    assertNoTagContains("seller title");
  }

  @Test
  void distinguishesInvalidOutputFromOtherProviderFailures() {
    var invalid = new InvalidAiResponseException();
    var provider = new AiProviderException(new RuntimeException("provider detail"));

    assertThatThrownBy(() -> telemetry.observe(() -> {
      throw invalid;
    })).isSameAs(invalid);
    assertThatThrownBy(() -> telemetry.observe(() -> {
      throw provider;
    })).isSameAs(provider);

    assertTimer("failure", "invalid_model_output", 1);
    assertTimer("failure", "provider_error", 1);
    assertNoTagContains("provider detail");
  }

  @Test
  void recordsErrorsWithTheCompleteFeatureTagSchema() {
    var failure = new AssertionError("seller data must never become a tag");

    assertThatThrownBy(() -> telemetry.observe(() -> {
      throw failure;
    })).isSameAs(failure);

    assertTimer("failure", "unexpected", 1);
    assertNoTagContains("seller data");
  }

  private void assertTimer(String result, String outcome, long count) {
    assertThat(meterRegistry.get("listing.quality.ai.feature")
        .tags(
            "feature", "listing-review",
            "route", "direct",
            "result", result,
            "outcome", outcome)
        .timer()
        .count()).isEqualTo(count);
  }

  private void assertNoTagContains(String sensitiveValue) {
    assertThat(meterRegistry.getMeters().stream()
        .flatMap(meter -> meter.getId().getTags().stream())
        .map(Tag::getValue))
        .noneMatch(value -> value.contains(sensitiveValue));
  }
}
