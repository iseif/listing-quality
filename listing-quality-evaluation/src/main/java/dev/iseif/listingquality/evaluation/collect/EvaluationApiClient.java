package dev.iseif.listingquality.evaluation.collect;

import dev.iseif.listingquality.evaluation.api.ApiProblem;
import dev.iseif.listingquality.evaluation.api.ListingDraftPayload;
import dev.iseif.listingquality.evaluation.api.ListingReviewPayload;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

public final class EvaluationApiClient implements ListingQualityClient {

  private final RestClient restClient;
  private final Sleeper sleeper;

  public EvaluationApiClient(
      RestClient.Builder builder, URI baseUrl, Duration timeout, Sleeper sleeper) {
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
    requestFactory.setReadTimeout(timeout);
    this.restClient = builder.baseUrl(baseUrl.toString())
        .requestFactory(requestFactory)
        .build();
    this.sleeper = sleeper;
  }

  EvaluationApiClient(RestClient.Builder builder, URI baseUrl, Sleeper sleeper) {
    this.restClient = builder.baseUrl(baseUrl.toString()).build();
    this.sleeper = sleeper;
  }

  @Override
  public void awaitReady(int attempts, Duration delay) {
    for (int attempt = 1; attempt <= attempts; attempt++) {
      if (isReady()) {
        return;
      }
      if (attempt < attempts) {
        try {
          sleeper.sleep(delay);
        } catch (InterruptedException exception) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException("Interrupted while waiting for service readiness");
        }
      }
    }
    String unit = attempts == 1 ? "attempt" : "attempts";
    throw new IllegalStateException(
        "Listing Quality service is not ready after %d %s".formatted(attempts, unit));
  }

  @Override
  public ReviewCallResult review(ListingDraftPayload listing) {
    long started = System.nanoTime();
    try {
      ListingReviewPayload review = restClient.post()
          .uri("/api/listings/review")
          .header("X-API-Version", "1")
          .contentType(MediaType.APPLICATION_JSON)
          .body(listing)
          .retrieve()
          .body(ListingReviewPayload.class);
      if (review == null) {
        return failure(null, "INVALID_RESPONSE", "CLIENT", started);
      }
      return new ReviewSuccess(review, elapsedMillis(started));
    } catch (RestClientResponseException exception) {
      ApiProblem problem = safeProblem(exception);
      String code = problem != null && problem.code() != null && !problem.code().isBlank()
          ? problem.code()
          : "HTTP_" + exception.getStatusCode().value();
      return failure(exception.getStatusCode().value(), code, "HTTP", started);
    } catch (RestClientException exception) {
      return failure(null, "INVALID_RESPONSE", "CLIENT", started);
    }
  }

  private boolean isReady() {
    try {
      Map<?, ?> health = restClient.get().uri("/actuator/health").retrieve().body(Map.class);
      return health != null && "UP".equals(health.get("status"));
    } catch (RestClientException exception) {
      return false;
    }
  }

  private ApiProblem safeProblem(RestClientResponseException exception) {
    try {
      return exception.getResponseBodyAs(ApiProblem.class);
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private ReviewFailure failure(
      Integer status, String code, String category, long started) {
    return new ReviewFailure(status, code, category, elapsedMillis(started));
  }

  private long elapsedMillis(long started) {
    return Duration.ofNanos(System.nanoTime() - started).toMillis();
  }
}
