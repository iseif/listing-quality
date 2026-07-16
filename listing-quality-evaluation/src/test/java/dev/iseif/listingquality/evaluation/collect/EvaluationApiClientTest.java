package dev.iseif.listingquality.evaluation.collect;

import dev.iseif.listingquality.evaluation.api.ListingDraftPayload;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class EvaluationApiClientTest {

  @Test
  void verifiesHealthAndSendsVersionedReviewRequest() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    EvaluationApiClient client = client(builder);

    server.expect(once(), requestTo("http://localhost:8080/actuator/health"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess("{\"status\":\"UP\"}", MediaType.APPLICATION_JSON));
    server.expect(once(), requestTo("http://localhost:8080/api/listings/review"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("X-API-Version", "1"))
        .andRespond(withSuccess("""
            {"qualityScore":70,"missingFields":["isbn"],"issues":[],
             "suggestions":["Add ISBN"],"requiresHumanReview":false}
            """, MediaType.APPLICATION_JSON));

    client.awaitReady(1, Duration.ZERO);
    ReviewCallResult result = client.review(listing());

    assertThat(result).isInstanceOfSatisfying(ReviewSuccess.class, success -> {
      assertThat(success.review().qualityScore()).isEqualTo(70);
      assertThat(success.durationMs()).isGreaterThanOrEqualTo(0);
    });
    server.verify();
  }

  @Test
  void stopsWhenHealthNeverBecomesUp() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    EvaluationApiClient client = client(builder);
    server.expect(requestTo("http://localhost:8080/actuator/health"))
        .andRespond(withSuccess("{\"status\":\"DOWN\"}", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.awaitReady(1, Duration.ZERO))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Listing Quality service is not ready after 1 attempt");
  }

  @Test
  void recordsSafeProblemDetailsWithoutRemoteDetailText() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    EvaluationApiClient client = client(builder);
    server.expect(requestTo("http://localhost:8080/api/listings/review"))
        .andRespond(withServiceUnavailable().body("""
            {"status":503,"title":"AI provider unavailable",
             "detail":"secret provider message and seller input",
             "code":"AI_PROVIDER_UNAVAILABLE"}
            """).contentType(MediaType.APPLICATION_PROBLEM_JSON));

    ReviewCallResult result = client.review(listing());

    assertThat(result).isEqualTo(new ReviewFailure(
        503, "AI_PROVIDER_UNAVAILABLE", "HTTP", ((ReviewFailure) result).durationMs()));
    assertThat(result.toString()).doesNotContain("secret provider message", "seller input");
  }

  @Test
  void recordsMalformedSuccessAsInvalidResponse() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    EvaluationApiClient client = client(builder);
    server.expect(requestTo("http://localhost:8080/api/listings/review"))
        .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));

    ReviewCallResult result = client.review(listing());

    assertThat(result).isInstanceOfSatisfying(ReviewFailure.class, failure -> {
      assertThat(failure.httpStatus()).isNull();
      assertThat(failure.errorCode()).isEqualTo("INVALID_RESPONSE");
      assertThat(failure.category()).isEqualTo("CLIENT");
    });
  }

  @Test
  void recordsUnknownHttpFailuresWithAStableCode() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    EvaluationApiClient client = client(builder);
    server.expect(requestTo("http://localhost:8080/api/listings/review"))
        .andRespond(withResourceNotFound());

    assertThat(client.review(listing())).isInstanceOfSatisfying(ReviewFailure.class, failure -> {
      assertThat(failure.httpStatus()).isEqualTo(404);
      assertThat(failure.errorCode()).isEqualTo("HTTP_404");
    });
  }

  private EvaluationApiClient client(RestClient.Builder builder) {
    return new EvaluationApiClient(
        builder, URI.create("http://localhost:8080"), duration -> {});
  }

  private ListingDraftPayload listing() {
    return new ListingDraftPayload("Book", "Used book", "Books", new BigDecimal("10"),
        Map.of("condition", "Used"));
  }
}
