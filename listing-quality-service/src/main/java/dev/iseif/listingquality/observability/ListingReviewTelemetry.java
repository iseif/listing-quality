package dev.iseif.listingquality.observability;

import dev.iseif.listingquality.model.ListingReview;
import dev.iseif.listingquality.service.exception.AiProviderException;
import dev.iseif.listingquality.service.exception.AiProviderUnavailableException;
import dev.iseif.listingquality.service.exception.InvalidAiResponseException;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.function.Supplier;

@Component
public final class ListingReviewTelemetry {

  static final String OBSERVATION_NAME = "listing.quality.ai.feature";

  private static final String TAG_FEATURE = "feature";
  private static final String TAG_ROUTE = "route";
  private static final String TAG_RESULT = "result";
  private static final String TAG_OUTCOME = "outcome";

  private final ObservationRegistry observationRegistry;

  public ListingReviewTelemetry(ObservationRegistry observationRegistry) {
    this.observationRegistry = Objects.requireNonNull(observationRegistry);
  }

  public ListingReview observe(Supplier<ListingReview> operation) {
    Objects.requireNonNull(operation);
    // Pessimistic result and outcome are seeded before the work runs, so the timer always carries
    // the same tag keys. A non-RuntimeException error (out of memory, for example) then stops the
    // observation through the finally block without being caught and turned into a metric tag.
    Observation observation = Observation.createNotStarted(OBSERVATION_NAME, observationRegistry)
        .contextualName("listing review")
        .lowCardinalityKeyValue(TAG_FEATURE, "listing-review")
        .lowCardinalityKeyValue(TAG_ROUTE, "direct")
        .lowCardinalityKeyValue(TAG_RESULT, "failure")
        .lowCardinalityKeyValue(TAG_OUTCOME, "unexpected")
        .start();
    try (var _ = observation.openScope()) {
      try {
        ListingReview review = operation.get();
        observation.lowCardinalityKeyValue(TAG_RESULT, "success")
            .lowCardinalityKeyValue(TAG_OUTCOME, "reviewed");
        return review;
      } catch (RuntimeException failure) {
        observation.error(failure)
            .lowCardinalityKeyValue(TAG_OUTCOME, outcome(failure));
        throw failure;
      }
    } finally {
      observation.stop();
    }
  }

  private String outcome(RuntimeException failure) {
    return switch (failure) {
      case AiProviderUnavailableException _ -> "provider_unavailable";
      case InvalidAiResponseException _ -> "invalid_model_output";
      case AiProviderException _ -> "provider_error";
      default -> "unexpected";
    };
  }
}
