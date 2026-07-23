package dev.iseif.listingquality.enrichment.service.execution;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

public final class ModelRoute {

  private final String providerId;
  private final Duration configuredTimeout;
  private final Retry retry;
  private final CircuitBreaker circuitBreaker;
  private final ExecutorService executor;

  public ModelRoute(
      String providerId,
      Duration configuredTimeout,
      Retry retry,
      CircuitBreaker circuitBreaker,
      ExecutorService executor) {
    this.providerId = Objects.requireNonNull(providerId);
    this.configuredTimeout = Objects.requireNonNull(configuredTimeout);
    this.retry = Objects.requireNonNull(retry);
    this.circuitBreaker = Objects.requireNonNull(circuitBreaker);
    this.executor = Objects.requireNonNull(executor);
  }

  public <T> T call(Supplier<T> action, Duration remainingOverallTime) {
    Duration effectiveTimeout = shorter(configuredTimeout, remainingOverallTime);
    if (effectiveTimeout.isZero() || effectiveTimeout.isNegative()) {
      throw timeoutFailure(new TimeoutException());
    }
    Supplier<T> retried = Retry.decorateSupplier(retry, action);
    return CircuitBreaker.decorateSupplier(
        circuitBreaker, () -> executeWithTimeout(retried, effectiveTimeout)).get();
  }

  public String providerId() {
    return providerId;
  }

  private <T> T executeWithTimeout(Supplier<T> action, Duration timeout) {
    TimeLimiter limiter = TimeLimiter.of(circuitBreaker.getName(), TimeLimiterConfig.custom()
        .timeoutDuration(timeout)
        .cancelRunningFuture(true)
        .build());
    try {
      return limiter.executeFutureSupplier(() -> executor.submit(action::get));
    } catch (TimeoutException exception) {
      throw timeoutFailure(exception);
    } catch (ExecutionException exception) {
      throw propagate(exception.getCause());
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw timeoutFailure(exception);
    } catch (RuntimeException exception) {
      throw exception;
    } catch (Exception exception) {
      throw ModelExecutionException.eligible(
          providerId, ModelFailureCategory.PROVIDER_UNAVAILABLE, exception);
    }
  }

  private ModelExecutionException timeoutFailure(Throwable cause) {
    return ModelExecutionException.eligible(
        providerId, ModelFailureCategory.PROVIDER_UNAVAILABLE, cause);
  }

  private RuntimeException propagate(Throwable cause) {
    if (cause instanceof RuntimeException runtimeException) {
      return runtimeException;
    }
    return ModelExecutionException.eligible(
        providerId, ModelFailureCategory.PROVIDER_UNAVAILABLE, cause);
  }

  private Duration shorter(Duration first, Duration second) {
    return first.compareTo(second) <= 0 ? first : second;
  }
}
