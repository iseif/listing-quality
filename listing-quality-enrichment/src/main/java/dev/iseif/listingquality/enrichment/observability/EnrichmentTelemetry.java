package dev.iseif.listingquality.enrichment.observability;

import dev.iseif.listingquality.enrichment.model.ExecutionMetadata;
import dev.iseif.listingquality.enrichment.model.ExecutionRoute;
import dev.iseif.listingquality.enrichment.model.book.BookEnrichmentResponse;
import dev.iseif.listingquality.enrichment.model.shoe.ShoeColorEnrichmentResponse;
import dev.iseif.listingquality.enrichment.service.book.exception.InvalidBookEnrichmentResponseException;
import dev.iseif.listingquality.enrichment.service.execution.ModelExecutionException;
import dev.iseif.listingquality.enrichment.service.shoe.InvalidShoeColorResponseException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

@Component
public final class EnrichmentTelemetry {

  static final String OBSERVATION_NAME = "listing.quality.ai.feature";
  static final String ROUTE_OBSERVATION_NAME = "listing.quality.ai.route";
  static final String ROUTE_FAILURES_COUNTER = "listing.quality.ai.route.failures";
  static final String FALLBACKS_COUNTER = "listing.quality.ai.fallbacks";

  // The label vocabulary is a contract, so its keys live in one place. A single constant per key
  // makes a typo in one of several usages a compile error instead of a silently forked series.
  private static final String TAG_FEATURE = "feature";
  private static final String TAG_ROUTE = "route";
  private static final String TAG_RESULT = "result";
  private static final String TAG_OUTCOME = "outcome";
  private static final String TAG_PROVIDER = "provider";
  private static final String TAG_CATEGORY = "category";
  private static final String TAG_REASON = "reason";

  private final ObservationRegistry observationRegistry;
  private final MeterRegistry meterRegistry;

  public EnrichmentTelemetry(
      ObservationRegistry observationRegistry,
      MeterRegistry meterRegistry) {
    this.observationRegistry = Objects.requireNonNull(observationRegistry);
    this.meterRegistry = Objects.requireNonNull(meterRegistry);
  }

  public BookEnrichmentResponse observeBook(Supplier<BookEnrichmentResponse> operation) {
    return observe(
        EnrichmentFeature.BOOK,
        operation,
        BookEnrichmentResponse::execution,
        response -> tagValue(response.status()));
  }

  public ShoeColorEnrichmentResponse observeShoeColor(
      Supplier<ShoeColorEnrichmentResponse> operation) {
    return observe(
        EnrichmentFeature.SHOE_COLOR,
        operation,
        ShoeColorEnrichmentResponse::execution,
        response -> tagValue(response.status()));
  }

  public <T> T observeRoute(
      EnrichmentFeature feature,
      ExecutionRoute route,
      String provider,
      Supplier<T> operation) {
    Objects.requireNonNull(operation);
    Observation observation = Observation.createNotStarted(
            ROUTE_OBSERVATION_NAME, observationRegistry)
        .contextualName(feature.tagValue() + " " + tagValue(route))
        .lowCardinalityKeyValue(TAG_FEATURE, feature.tagValue())
        .lowCardinalityKeyValue(TAG_ROUTE, tagValue(route))
        .lowCardinalityKeyValue(TAG_PROVIDER, providerTag(provider))
        .lowCardinalityKeyValue(TAG_RESULT, "failure")
        .lowCardinalityKeyValue(TAG_OUTCOME, "unexpected")
        .start();
    try (var _ = observation.openScope()) {
      try {
        T result = operation.get();
        observation.lowCardinalityKeyValue(TAG_RESULT, "success")
            .lowCardinalityKeyValue(TAG_OUTCOME, "accepted");
        return result;
      } catch (RuntimeException failure) {
        observation.error(failure)
            .lowCardinalityKeyValue(TAG_OUTCOME, failureCategory(failure));
        throw failure;
      }
    } finally {
      observation.stop();
    }
  }

  public void recordRouteFailure(
      EnrichmentFeature feature,
      ExecutionRoute route,
      Throwable failure) {
    Counter.builder(ROUTE_FAILURES_COUNTER)
        .tag(TAG_FEATURE, Objects.requireNonNull(feature).tagValue())
        .tag(TAG_ROUTE, tagValue(Objects.requireNonNull(route)))
        .tag(TAG_CATEGORY, failureCategory(Objects.requireNonNull(failure)))
        .register(meterRegistry)
        .increment();
  }

  public void recordFallback(EnrichmentFeature feature, Throwable primaryFailure) {
    incrementFallback(feature, failureCategory(Objects.requireNonNull(primaryFailure)));
  }

  public void recordInconclusiveFallback(EnrichmentFeature feature) {
    incrementFallback(feature, "inconclusive");
  }

  private <T> T observe(
      EnrichmentFeature feature,
      Supplier<T> operation,
      Function<T, ExecutionMetadata> execution,
      Function<T, String> outcome) {
    Objects.requireNonNull(operation);
    // Pessimistic route, result, and outcome are seeded before the work runs, so the timer always
    // carries the same tag keys. A non-RuntimeException error then stops the observation through
    // the finally block without being caught and turned into a metric tag.
    Observation observation = Observation.createNotStarted(OBSERVATION_NAME, observationRegistry)
        .contextualName(feature.tagValue())
        .lowCardinalityKeyValue(TAG_FEATURE, feature.tagValue())
        .lowCardinalityKeyValue(TAG_ROUTE, "none")
        .lowCardinalityKeyValue(TAG_RESULT, "failure")
        .lowCardinalityKeyValue(TAG_OUTCOME, "unexpected")
        .start();
    try (var _ = observation.openScope()) {
      try {
        T response = operation.get();
        observation.lowCardinalityKeyValue(TAG_ROUTE, tagValue(execution.apply(response).route()))
            .lowCardinalityKeyValue(TAG_RESULT, "success")
            .lowCardinalityKeyValue(TAG_OUTCOME, outcome.apply(response));
        return response;
      } catch (RuntimeException failure) {
        observation.error(failure)
            .lowCardinalityKeyValue(TAG_OUTCOME, failureCategory(failure));
        throw failure;
      }
    } finally {
      observation.stop();
    }
  }

  private void incrementFallback(EnrichmentFeature feature, String reason) {
    Counter.builder(FALLBACKS_COUNTER)
        .tag(TAG_FEATURE, Objects.requireNonNull(feature).tagValue())
        .tag(TAG_REASON, reason)
        .register(meterRegistry)
        .increment();
  }

  private String failureCategory(Throwable failure) {
    return switch (failure) {
      case ModelExecutionException modelFailure -> tagValue(modelFailure.category());
      case CallNotPermittedException _ -> "circuit_open";
      case InvalidBookEnrichmentResponseException _ -> "invalid_grounding";
      case InvalidShoeColorResponseException _ -> "invalid_model_output";
      default -> "unexpected";
    };
  }

  private String providerTag(String provider) {
    return switch (Objects.requireNonNull(provider)) {
      case "gemini", "omlx" -> provider;
      default -> "unknown";
    };
  }

  private String tagValue(Enum<?> value) {
    return value.name().toLowerCase(Locale.ROOT);
  }
}
