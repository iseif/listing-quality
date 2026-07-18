package dev.iseif.listingquality.enrichment.service.book;

import dev.iseif.listingquality.enrichment.catalog.book.BookCatalogEvidenceLedger;
import dev.iseif.listingquality.enrichment.catalog.book.BookCatalogTools;
import dev.iseif.listingquality.enrichment.model.ExecutionRoute;
import dev.iseif.listingquality.enrichment.model.book.BookEnrichmentRequest;
import dev.iseif.listingquality.enrichment.model.book.GeneratedBookEnrichment;
import dev.iseif.listingquality.enrichment.service.book.exception.InvalidBookEnrichmentResponseException;
import dev.iseif.listingquality.enrichment.service.execution.EnrichmentFailureClassifier;
import dev.iseif.listingquality.enrichment.service.execution.ModelExecutionException;
import dev.iseif.listingquality.enrichment.service.execution.ModelFailureCategory;
import dev.iseif.listingquality.enrichment.service.execution.ModelRoute;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;

public final class FailoverBookEnrichmentGenerator {

  private static final Logger log =
      LoggerFactory.getLogger(FailoverBookEnrichmentGenerator.class);

  private final BookEnrichmentGenerator primaryGenerator;
  private final BookEnrichmentGenerator fallbackGenerator;
  private final ModelRoute primaryRoute;
  private final ModelRoute fallbackRoute;
  private final BookEnrichmentValidator validator;
  private final EnrichmentFailureClassifier classifier;
  private final Duration overallTimeout;

  public FailoverBookEnrichmentGenerator(
      BookEnrichmentGenerator primaryGenerator,
      BookEnrichmentGenerator fallbackGenerator,
      ModelRoute primaryRoute,
      ModelRoute fallbackRoute,
      BookEnrichmentValidator validator,
      EnrichmentFailureClassifier classifier,
      Duration overallTimeout) {
    this.primaryGenerator = Objects.requireNonNull(primaryGenerator);
    this.fallbackGenerator = Objects.requireNonNull(fallbackGenerator);
    this.primaryRoute = Objects.requireNonNull(primaryRoute);
    this.fallbackRoute = Objects.requireNonNull(fallbackRoute);
    this.validator = Objects.requireNonNull(validator);
    this.classifier = Objects.requireNonNull(classifier);
    this.overallTimeout = Objects.requireNonNull(overallTimeout);
  }

  public BookEnrichmentExecution execute(
      BookEnrichmentRequest request,
      String prompt,
      BookCatalogTools tools,
      BookCatalogEvidenceLedger ledger) {
    long startedAt = System.nanoTime();
    try {
      GeneratedBookEnrichment generated = primaryRoute.call(
          () -> primaryGenerator.generate(prompt, tools), remaining(startedAt));
      return validated(request, generated, ledger, ExecutionRoute.PRIMARY);
    } catch (RuntimeException primaryFailure) {
      logRouteExecutionFailure(ExecutionRoute.PRIMARY, primaryFailure);
      if (!isFallbackEligible(primaryFailure)) {
        throw primaryFailure;
      }
      try {
        GeneratedBookEnrichment generated = fallbackRoute.call(
            () -> fallbackGenerator.generate(prompt, tools), remaining(startedAt));
        return validated(request, generated, ledger, ExecutionRoute.FALLBACK);
      } catch (RuntimeException fallbackFailure) {
        logRouteExecutionFailure(ExecutionRoute.FALLBACK, fallbackFailure);
        throw terminalFailure(primaryFailure, fallbackFailure);
      }
    }
  }

  private BookEnrichmentExecution validated(
      BookEnrichmentRequest request,
      GeneratedBookEnrichment generated,
      BookCatalogEvidenceLedger ledger,
      ExecutionRoute route) {
    return new BookEnrichmentExecution(validator.validate(request, generated, ledger), route);
  }

  private boolean isFallbackEligible(Throwable failure) {
    return classifier.isFallbackEligible(failure)
        || failure instanceof InvalidBookEnrichmentResponseException;
  }

  /**
   * The single place a route failure is recorded. The exception itself carries no route, and it
   * is rethrown rather than handled here, so logging it once at the point where the route is
   * known avoids both a silent failure and a duplicated report.
   */
  private void logRouteExecutionFailure(ExecutionRoute route, RuntimeException failure) {
    switch (failure) {
      case ModelExecutionException modelFailure -> log.warn(
          "Enrichment route execution failed: route={}, provider={}, category={}",
          route, modelFailure.providerId(), modelFailure.category());
      case CallNotPermittedException _ -> log.warn(
          "Enrichment route execution failed: route={}, category=CIRCUIT_OPEN", route);
      case InvalidBookEnrichmentResponseException groundingFailure -> log.warn(
          "Enrichment route failed grounding validation: route={}, failure={}, field={}",
          route, groundingFailure.failure(), groundingFailure.field());
      default -> log.warn(
          "Enrichment route execution failed: route={}, type={}",
          route, failure.getClass().getSimpleName());
    }
  }

  private RuntimeException terminalFailure(
      RuntimeException primaryFailure,
      RuntimeException fallbackFailure) {
    if (!isFallbackEligible(fallbackFailure)) {
      return fallbackFailure;
    }
    if (isAvailability(fallbackFailure)) {
      fallbackFailure.addSuppressed(primaryFailure);
      return fallbackFailure;
    }
    if (isAvailability(primaryFailure)) {
      primaryFailure.addSuppressed(fallbackFailure);
      return primaryFailure;
    }
    return new InvalidBookEnrichmentResponseException(fallbackFailure);
  }

  private boolean isAvailability(Throwable failure) {
    return failure instanceof ModelExecutionException modelFailure
        && (modelFailure.category() == ModelFailureCategory.PROVIDER_UNAVAILABLE
            || modelFailure.category() == ModelFailureCategory.RATE_LIMITED
            || modelFailure.category() == ModelFailureCategory.QUOTA_EXHAUSTED);
  }

  private Duration remaining(long startedAt) {
    Duration remaining = overallTimeout.minusNanos(Math.max(0, System.nanoTime() - startedAt));
    return remaining.isNegative() ? Duration.ZERO : remaining;
  }
}
