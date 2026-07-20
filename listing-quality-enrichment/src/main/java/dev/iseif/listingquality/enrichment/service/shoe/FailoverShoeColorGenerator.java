package dev.iseif.listingquality.enrichment.service.shoe;

import dev.iseif.listingquality.enrichment.media.ProductImage;
import dev.iseif.listingquality.enrichment.model.ExecutionRoute;
import dev.iseif.listingquality.enrichment.model.shoe.GeneratedShoeColorExtraction;
import dev.iseif.listingquality.enrichment.model.shoe.ShoeColorOutcome;
import dev.iseif.listingquality.enrichment.service.execution.EnrichmentFailureClassifier;
import dev.iseif.listingquality.enrichment.service.execution.ModelExecutionException;
import dev.iseif.listingquality.enrichment.service.execution.ModelFailureCategory;
import dev.iseif.listingquality.enrichment.service.execution.ModelRoute;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

public final class FailoverShoeColorGenerator {

  private static final Logger log = LoggerFactory.getLogger(FailoverShoeColorGenerator.class);

  private final ShoeColorGenerator primaryGenerator;
  private final ShoeColorGenerator fallbackGenerator;
  private final ModelRoute primaryRoute;
  private final ModelRoute fallbackRoute;
  private final ShoeColorExtractionValidator validator;
  private final EnrichmentFailureClassifier classifier;
  private final Duration overallTimeout;

  public FailoverShoeColorGenerator(
      ShoeColorGenerator primaryGenerator,
      ShoeColorGenerator fallbackGenerator,
      ModelRoute primaryRoute,
      ModelRoute fallbackRoute,
      ShoeColorExtractionValidator validator,
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

  public ShoeColorExecution execute(String prompt, List<ProductImage> images) {
    long startedAt = System.nanoTime();
    try {
      ShoeColorExecution primary = executeRoute(
          primaryGenerator,
          primaryRoute,
          prompt,
          images,
          ExecutionRoute.PRIMARY,
          startedAt);
      if (primary.extraction().outcome() == ShoeColorOutcome.COLORS_OBSERVED) {
        return primary;
      }
      log.warn("Shoe color route was inconclusive: route={}", ExecutionRoute.PRIMARY);
    } catch (RuntimeException primaryFailure) {
      logRouteFailure(ExecutionRoute.PRIMARY, primaryFailure);
      if (!isFallbackEligible(primaryFailure)) {
        throw primaryFailure;
      }
      return executeFallback(prompt, images, startedAt, primaryFailure);
    }

    return executeFallback(prompt, images, startedAt, null);
  }

  private ShoeColorExecution executeFallback(
      String prompt,
      List<ProductImage> images,
      long startedAt,
      RuntimeException primaryFailure) {
    try {
      return executeRoute(
          fallbackGenerator,
          fallbackRoute,
          prompt,
          images,
          ExecutionRoute.FALLBACK,
          startedAt);
    } catch (RuntimeException fallbackFailure) {
      logRouteFailure(ExecutionRoute.FALLBACK, fallbackFailure);
      if (primaryFailure == null) {
        throw fallbackFailure;
      }
      throw terminalFailure(primaryFailure, fallbackFailure);
    }
  }

  private ShoeColorExecution executeRoute(
      ShoeColorGenerator generator,
      ModelRoute route,
      String prompt,
      List<ProductImage> images,
      ExecutionRoute executionRoute,
      long startedAt) {
    GeneratedShoeColorExtraction generated = route.call(
        () -> generator.generate(prompt, images), remaining(startedAt));
    return new ShoeColorExecution(validator.validate(generated, images), executionRoute);
  }

  private boolean isFallbackEligible(Throwable failure) {
    return classifier.isFallbackEligible(failure)
        || failure instanceof InvalidShoeColorResponseException;
  }

  private void logRouteFailure(ExecutionRoute route, RuntimeException failure) {
    switch (failure) {
      case ModelExecutionException modelFailure -> log.warn(
          "Shoe color route execution failed: route={}, provider={}, category={}",
          route, modelFailure.providerId(), modelFailure.category());
      case CallNotPermittedException _ -> log.warn(
          "Shoe color route execution failed: route={}, category=CIRCUIT_OPEN", route);
      case InvalidShoeColorResponseException validationFailure -> log.warn(
          "Shoe color route failed validation: route={}, failure={}, imageId={}",
          route, validationFailure.failure(), validationFailure.imageId());
      default -> log.warn(
          "Shoe color route execution failed: route={}, type={}",
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
    return fallbackFailure;
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
