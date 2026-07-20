package dev.iseif.listingquality.enrichment.service.execution;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelRouteTest {

  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
  private final EnrichmentFailureClassifier classifier = new EnrichmentFailureClassifier();

  @AfterEach
  void closeExecutor() {
    executor.close();
  }

  @Test
  void retriesOnlyEligibleTransportFailures() {
    ModelRoute route = route(2, Duration.ofSeconds(1), 10);
    AtomicInteger calls = new AtomicInteger();

    String result = route.call(() -> {
      if (calls.incrementAndGet() == 1) {
        throw ModelExecutionException.eligible(
            "gemini", ModelFailureCategory.PROVIDER_UNAVAILABLE, new RuntimeException());
      }
      return "ok";
    }, Duration.ofSeconds(1));

    assertThat(result).isEqualTo("ok");
    assertThat(calls).hasValue(2);
  }

  @Test
  void doesNotTransportRetryInvalidStructuredOutput() {
    ModelRoute route = route(2, Duration.ofSeconds(1), 10);
    AtomicInteger calls = new AtomicInteger();
    Supplier<String> invalidOutput = failingCall(calls, ModelFailureCategory.INVALID_MODEL_OUTPUT);

    assertThatThrownBy(() -> route.call(invalidOutput, Duration.ofSeconds(1)))
        .isInstanceOf(ModelExecutionException.class);
    assertThat(calls).hasValue(1);
  }

  @Test
  void convertsTimeoutToAConciseEligibleFailure() {
    ModelRoute route = route(1, Duration.ofMillis(30), 10);
    Supplier<String> neverCompletes = blockingCall();

    assertThatThrownBy(() -> route.call(neverCompletes, Duration.ofSeconds(1)))
        .isInstanceOfSatisfying(ModelExecutionException.class, failure -> {
          assertThat(failure.category()).isEqualTo(ModelFailureCategory.PROVIDER_UNAVAILABLE);
          assertThat(failure.eligible()).isTrue();
          assertThat(failure.getMessage()).isEqualTo("AI model execution failed");
        });
  }

  @Test
  void opensTheCircuitAfterTheConfiguredFailureWindow() {
    ModelRoute route = route(1, Duration.ofSeconds(1), 2);
    AtomicInteger calls = new AtomicInteger();
    Supplier<String> unavailable = failingCall(calls, ModelFailureCategory.PROVIDER_UNAVAILABLE);

    for (int index = 0; index < 2; index++) {
      assertThatThrownBy(() -> route.call(unavailable, Duration.ofSeconds(1)))
          .isInstanceOf(ModelExecutionException.class);
    }

    Supplier<String> notCalled = () -> {
      calls.incrementAndGet();
      return "not called";
    };
    assertThatThrownBy(() -> route.call(notCalled, Duration.ofSeconds(1)))
        .isInstanceOf(CallNotPermittedException.class);
    assertThat(calls).hasValue(2);
  }

  @Test
  void recordsTimedOutProviderPathsAgainstTheCircuit() {
    ModelRoute route = route(1, Duration.ofMillis(20), 2);
    Supplier<String> neverCompletes = blockingCall();

    for (int index = 0; index < 2; index++) {
      assertThatThrownBy(() -> route.call(neverCompletes, Duration.ofSeconds(1)))
          .isInstanceOf(ModelExecutionException.class);
    }

    assertThatThrownBy(() -> route.call(() -> "not called", Duration.ofSeconds(1)))
        .isInstanceOf(CallNotPermittedException.class);
  }

  @Test
  void keepsCircuitStateIsolatedByRouteIdentifier() {
    ModelRoute bookRoute = route("book-gemini", 1, Duration.ofSeconds(1), 2);
    ModelRoute shoeRoute = route("shoe-color-gemini", 1, Duration.ofSeconds(1), 2);
    AtomicInteger bookCalls = new AtomicInteger();
    Supplier<String> unavailable = failingCall(
        bookCalls, ModelFailureCategory.PROVIDER_UNAVAILABLE);

    for (int index = 0; index < 2; index++) {
      assertThatThrownBy(() -> bookRoute.call(unavailable, Duration.ofSeconds(1)))
          .isInstanceOf(ModelExecutionException.class);
    }

    assertThat(shoeRoute.call(() -> "available", Duration.ofSeconds(1)))
        .isEqualTo("available");
  }

  private Supplier<String> failingCall(AtomicInteger calls, ModelFailureCategory category) {
    return () -> {
      calls.incrementAndGet();
      throw ModelExecutionException.eligible("gemini", category, new RuntimeException());
    };
  }

  /**
   * Blocks until the time limiter cancels it, rather than sleeping for a fixed guess. The test
   * therefore costs the route timeout instead of an arbitrary duration, and it verifies that
   * cancellation actually interrupts the call.
   */
  private Supplier<String> blockingCall() {
    return () -> {
      try {
        new CountDownLatch(1).await();
      } catch (InterruptedException _) {
        Thread.currentThread().interrupt();
      }
      return "late";
    };
  }

  private ModelRoute route(int attempts, Duration timeout, int window) {
    return route("test-gemini", attempts, timeout, window);
  }

  private ModelRoute route(String routeId, int attempts, Duration timeout, int window) {
    return new ModelRoute(
        routeId,
        "gemini",
        timeout,
        new RoutePolicy(attempts, Duration.ofMillis(100), window, 50, Duration.ofSeconds(30), 1),
        classifier,
        executor);
  }
}
