package dev.iseif.listingquality.controller;

import dev.iseif.listingquality.model.ListingReview;
import dev.iseif.listingquality.service.ListingReviewService;
import dev.iseif.listingquality.service.exception.AiProviderUnavailableException;
import dev.iseif.listingquality.service.exception.InvalidAiResponseException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@WebMvcTest(ListingReviewController.class)
class ListingReviewControllerTest {

  private static final String VALID_BODY = """
      {
        "title": "Wireless keyboard",
        "description": "Nice keyboard, used only a few times.",
        "category": "Computer accessories",
        "price": 45.00,
        "attributes": { "brand": "KeyPro" }
      }
      """;

  @Autowired
  private MockMvcTester mockMvc;

  @MockitoBean
  private ListingReviewService listingReviewService;

  private MvcTestResult postJson(String body) {
    return mockMvc.post().uri("/api/listings/review")
        .contentType(MediaType.APPLICATION_JSON).content(body).exchange();
  }

  @Test
  void returnsTheReviewForAValidRequest() {
    given(listingReviewService.review(any())).willReturn(
        new ListingReview(72, List.of("layout"), List.of(), List.of("Add layout"), false));

    MvcTestResult result = postJson(VALID_BODY);

    assertThat(result).hasStatusOk();
    assertThat(result).bodyJson().extractingPath("$.qualityScore").isEqualTo(72);
    assertThat(result).bodyJson().extractingPath("$.missingFields[0]").isEqualTo("layout");
  }

  @Test
  void servesTheEndpointWhenAnExplicitSupportedVersionIsRequested() {
    given(listingReviewService.review(any())).willReturn(
        new ListingReview(60, List.of(), List.of(), List.of(), false));

    MvcTestResult result = mockMvc.post().uri("/api/listings/review")
        .header("X-API-Version", "1")
        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY).exchange();

    assertThat(result).hasStatusOk();
    assertThat(result).bodyJson().extractingPath("$.qualityScore").isEqualTo(60);
  }

  @Test
  void rejectsAnUnsupportedApiVersion() {
    MvcTestResult result = mockMvc.post().uri("/api/listings/review")
        .header("X-API-Version", "999")
        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY).exchange();

    assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("BAD_REQUEST");
  }

  @Test
  void mapsTransientProviderFailureToServiceUnavailable() {
    given(listingReviewService.review(any()))
        .willThrow(new AiProviderUnavailableException(new RuntimeException("boom")));

    MvcTestResult result = postJson(VALID_BODY);

    assertThat(result).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("AI_PROVIDER_UNAVAILABLE");
    assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("urn:problem:ai-provider-unavailable");
    assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("AI provider unavailable");
    assertThat(result).bodyJson().doesNotHavePath("$.trace");
    assertThat(result).bodyJson().extractingPath("$.detail")
        .isEqualTo("Listing review is temporarily unavailable. Please try again.");
  }

  @Test
  void mapsInvalidModelResponseToBadGateway() {
    given(listingReviewService.review(any())).willThrow(new InvalidAiResponseException());

    MvcTestResult result = postJson(VALID_BODY);

    assertThat(result).hasStatus(HttpStatus.BAD_GATEWAY);
    assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("AI_RESPONSE_INVALID");
  }

  @Test
  void rejectsAnInvalidRequestBodyWithBadRequest() {
    MvcTestResult result = postJson(VALID_BODY.replace("Wireless keyboard", ""));

    assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("BAD_REQUEST");
    assertThat(result).bodyJson().extractingPath("$.instance").isEqualTo("/api/listings/review");
  }

  @Test
  void rejectsAnOversizedDescriptionWithBadRequest() {
    String body = VALID_BODY.replace("Nice keyboard, used only a few times.", "a".repeat(5001));

    assertThat(postJson(body)).hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson().extractingPath("$.code").isEqualTo("BAD_REQUEST");
  }

  @Test
  void rejectsAMissingRequiredFieldWithBadRequest() {
    String noPrice = """
        {
          "title": "Wireless keyboard",
          "description": "Nice keyboard.",
          "category": "Computer accessories"
        }
        """;

    assertThat(postJson(noPrice)).hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson().extractingPath("$.code").isEqualTo("BAD_REQUEST");
  }

  @Test
  void rejectsMalformedJsonWithBadRequest() {
    assertThat(postJson("{ not valid json ")).hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson().extractingPath("$.code").isEqualTo("BAD_REQUEST");
  }

  @Test
  void rejectsAnUnsupportedHttpMethodWithMethodNotAllowed() {
    MvcTestResult result = mockMvc.get().uri("/api/listings/review").exchange();

    assertThat(result).hasStatus(HttpStatus.METHOD_NOT_ALLOWED);
    assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("METHOD_NOT_ALLOWED");
    assertThat(result).bodyJson().extractingPath("$.instance").isEqualTo("/api/listings/review");
  }

  @Test
  void rejectsAnUnsupportedContentTypeWithUnsupportedMediaType() {
    MvcTestResult result = mockMvc.post().uri("/api/listings/review")
        .contentType(MediaType.TEXT_PLAIN).content("hello").exchange();

    assertThat(result).hasStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("UNSUPPORTED_MEDIA_TYPE");
  }
}
