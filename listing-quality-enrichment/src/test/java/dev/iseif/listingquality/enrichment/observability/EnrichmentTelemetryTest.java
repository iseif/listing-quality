package dev.iseif.listingquality.enrichment.observability;

import dev.iseif.listingquality.enrichment.model.ExecutionMetadata;
import dev.iseif.listingquality.enrichment.model.ExecutionRoute;
import dev.iseif.listingquality.enrichment.model.book.BookEnrichmentResponse;
import dev.iseif.listingquality.enrichment.model.book.BookField;
import dev.iseif.listingquality.enrichment.model.book.EnrichmentStatus;
import dev.iseif.listingquality.enrichment.model.shoe.ShoeColorEnrichmentResponse;
import dev.iseif.listingquality.enrichment.model.shoe.ShoeColorStatus;
import dev.iseif.listingquality.enrichment.service.book.exception.BookEnrichmentValidationFailure;
import dev.iseif.listingquality.enrichment.service.book.exception.InvalidBookEnrichmentResponseException;
import dev.iseif.listingquality.enrichment.service.execution.ModelExecutionException;
import dev.iseif.listingquality.enrichment.service.execution.ModelFailureCategory;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnrichmentTelemetryTest {

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final ObservationRegistry observationRegistry = ObservationRegistry.create();
  private EnrichmentTelemetry telemetry;

  @BeforeEach
  void setUp() {
    observationRegistry.observationConfig()
        .observationHandler(new DefaultMeterObservationHandler(meterRegistry));
    telemetry = new EnrichmentTelemetry(observationRegistry, meterRegistry);
  }

  @Test
  void recordsBookStatusAndPrimaryRoute() {
    BookEnrichmentResponse response = bookResponse(ExecutionRoute.PRIMARY);

    assertThat(telemetry.observeBook(() -> response)).isSameAs(response);

    assertFeatureTimer(
        "book-enrichment", "primary", "success", "no_safe_proposals", 1);
  }

  @Test
  void recordsShoeStatusAndFallbackRoute() {
    ShoeColorEnrichmentResponse response = shoeResponse(ExecutionRoute.FALLBACK);

    assertThat(telemetry.observeShoeColor(() -> response)).isSameAs(response);

    assertFeatureTimer(
        "shoe-color-enrichment", "fallback", "success", "needs_seller_input", 1);
  }

  @Test
  void recordsBoundedFailureWithoutChangingTheException() {
    var failure = ModelExecutionException.eligible(
        "gemini",
        ModelFailureCategory.PROVIDER_UNAVAILABLE,
        new RuntimeException("listing book-123 must not become a tag"));

    assertThatThrownBy(() -> telemetry.observeBook(() -> {
      throw failure;
    })).isSameAs(failure);

    assertFeatureTimer(
        "book-enrichment", "none", "failure", "provider_unavailable", 1);
    assertNoTagContains("book-123");
  }

  @Test
  void recordsRouteExecutionWithBoundedProviderAndRouteTags() {
    assertThat(telemetry.observeRoute(
        EnrichmentFeature.BOOK,
        ExecutionRoute.PRIMARY,
        "gemini",
        () -> "accepted")).isEqualTo("accepted");

    assertThat(meterRegistry.get("listing.quality.ai.route")
        .tags(
            "feature", "book-enrichment",
            "route", "primary",
            "provider", "gemini",
            "result", "success",
            "outcome", "accepted")
        .timer()
        .count()).isEqualTo(1);
  }

  @Test
  void recordsErrorsWithTheCompleteFeatureTagSchema() {
    var failure = new AssertionError("seller data must never become a tag");

    assertThatThrownBy(() -> telemetry.observeBook(() -> {
      throw failure;
    })).isSameAs(failure);

    assertFeatureTimer("book-enrichment", "none", "failure", "unexpected", 1);
    assertNoTagContains("seller data");
  }

  @Test
  void recordsFallbackAndRouteFailureOnce() {
    var groundingFailure = new InvalidBookEnrichmentResponseException(
        BookEnrichmentValidationFailure.EVIDENCE_VALUE_MISMATCH,
        BookField.TITLE);

    telemetry.recordRouteFailure(
        EnrichmentFeature.BOOK, ExecutionRoute.PRIMARY, groundingFailure);
    telemetry.recordFallback(EnrichmentFeature.BOOK, groundingFailure);

    assertCounter(
        "listing.quality.ai.route.failures",
        "feature", "book-enrichment",
        "route", "primary",
        "category", "invalid_grounding");
    assertCounter(
        "listing.quality.ai.fallbacks",
        "feature", "book-enrichment",
        "reason", "invalid_grounding");
  }

  @Test
  void recordsCircuitOpenAndInconclusiveAsClosedValues() {
    CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("shoe-color-omlx-primary");
    circuitBreaker.transitionToOpenState();
    CallNotPermittedException circuitOpen =
        CallNotPermittedException.createCallNotPermittedException(circuitBreaker);

    telemetry.recordRouteFailure(
        EnrichmentFeature.SHOE_COLOR, ExecutionRoute.PRIMARY, circuitOpen);
    telemetry.recordInconclusiveFallback(EnrichmentFeature.SHOE_COLOR);

    assertCounter(
        "listing.quality.ai.route.failures",
        "feature", "shoe-color-enrichment",
        "route", "primary",
        "category", "circuit_open");
    assertCounter(
        "listing.quality.ai.fallbacks",
        "feature", "shoe-color-enrichment",
        "reason", "inconclusive");
  }

  private void assertFeatureTimer(
      String feature, String route, String result, String outcome, long count) {
    assertThat(meterRegistry.get("listing.quality.ai.feature")
        .tags(
            "feature", feature,
            "route", route,
            "result", result,
            "outcome", outcome)
        .timer()
        .count()).isEqualTo(count);
  }

  private void assertCounter(String name, String... tags) {
    assertThat(meterRegistry.get(name).tags(tags).counter().count()).isEqualTo(1);
  }

  private void assertNoTagContains(String sensitiveValue) {
    assertThat(meterRegistry.getMeters().stream()
        .flatMap(meter -> meter.getId().getTags().stream())
        .map(Tag::getValue))
        .noneMatch(value -> value.contains(sensitiveValue));
  }

  private BookEnrichmentResponse bookResponse(ExecutionRoute route) {
    return new BookEnrichmentResponse(
        EnrichmentStatus.NO_SAFE_PROPOSALS,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        false,
        new ExecutionMetadata(route));
  }

  private ShoeColorEnrichmentResponse shoeResponse(ExecutionRoute route) {
    return new ShoeColorEnrichmentResponse(
        ShoeColorStatus.NEEDS_SELLER_INPUT,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        false,
        new ExecutionMetadata(route));
  }
}
