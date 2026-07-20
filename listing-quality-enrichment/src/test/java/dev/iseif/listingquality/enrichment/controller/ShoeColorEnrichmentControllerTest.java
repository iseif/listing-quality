package dev.iseif.listingquality.enrichment.controller;

import dev.iseif.listingquality.enrichment.media.exception.ImageSourceUnavailableException;
import dev.iseif.listingquality.enrichment.media.exception.RejectedImageException;
import dev.iseif.listingquality.enrichment.model.ExecutionMetadata;
import dev.iseif.listingquality.enrichment.model.ExecutionRoute;
import dev.iseif.listingquality.enrichment.model.shoe.ShoeColorEnrichmentResponse;
import dev.iseif.listingquality.enrichment.model.shoe.ShoeColorStatus;
import dev.iseif.listingquality.enrichment.service.execution.ModelExecutionException;
import dev.iseif.listingquality.enrichment.service.execution.ModelFailureCategory;
import dev.iseif.listingquality.enrichment.service.shoe.InvalidShoeColorResponseException;
import dev.iseif.listingquality.enrichment.service.shoe.ShoeColorEnrichmentService;
import dev.iseif.listingquality.enrichment.service.shoe.ShoeColorValidationFailure;
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
import static org.mockito.BDDMockito.willThrow;

@WebMvcTest(ShoeColorEnrichmentController.class)
class ShoeColorEnrichmentControllerTest {

  private static final String VALID_BODY = """
      {
        "listingId": "shoe-123",
        "listingColors": ["GREEN", "WHITE"],
        "imageUrls": ["https://cdn.example.com/shoe.jpg?token=secret"]
      }
      """;

  @Autowired
  private MockMvcTester mockMvc;

  @MockitoBean
  private ShoeColorEnrichmentService service;

  @Test
  void returnsAResponseForTheVersionedEndpoint() {
    given(service.enrich(any())).willReturn(response());

    MvcTestResult result = post(VALID_BODY, "1");

    assertThat(result).hasStatusOk();
    assertThat(result).bodyJson().extractingPath("$.status")
        .isEqualTo("COLORS_CONFIRMED");
  }

  @Test
  void rejectsInvalidRequestCardinalityAndUnsupportedVersions() {
    String noImages = VALID_BODY.replace(
        "[\"https://cdn.example.com/shoe.jpg?token=secret\"]", "[]");
    String fourImages = VALID_BODY.replace(
        "[\"https://cdn.example.com/shoe.jpg?token=secret\"]",
        "[\"https://cdn.example.com/1.jpg\",\"https://cdn.example.com/2.jpg\","
            + "\"https://cdn.example.com/3.jpg\",\"https://cdn.example.com/4.jpg\"]");

    assertBadRequest(VALID_BODY.replace("shoe-123", ""), "1");
    assertBadRequest(noImages, "1");
    assertBadRequest(fourImages, "1");
    assertBadRequest(VALID_BODY, "999");
  }

  @Test
  void returnsAConciseRejectedImageProblem() {
    given(service.enrich(any())).willThrow(
        new RejectedImageException("Image URL is not allowed"));

    MvcTestResult result = post(VALID_BODY, "1");

    assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
    assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("IMAGE_REJECTED");
    assertThat(result).bodyText().doesNotContain("token=secret", "cdn.example.com");
  }

  @Test
  void distinguishesImageSourceModelOutputAndProviderFailures() {
    given(service.enrich(any())).willThrow(
        new ImageSourceUnavailableException("Image source is unavailable"));
    assertThat(post(VALID_BODY, "1")).hasStatus(HttpStatus.BAD_GATEWAY)
        .bodyJson().extractingPath("$.code").isEqualTo("IMAGE_SOURCE_UNAVAILABLE");

    willThrow(new InvalidShoeColorResponseException(
        ShoeColorValidationFailure.EVIDENCE_REFERENCE_INVALID, "IMAGE_9"))
        .given(service).enrich(any());
    assertThat(post(VALID_BODY, "1")).hasStatus(HttpStatus.BAD_GATEWAY)
        .bodyJson().extractingPath("$.code").isEqualTo("ENRICHMENT_RESPONSE_INVALID");

    willThrow(ModelExecutionException.eligible(
        "omlx",
        ModelFailureCategory.PROVIDER_UNAVAILABLE,
        new RuntimeException("provider secret detail")))
        .given(service).enrich(any());
    MvcTestResult unavailable = post(VALID_BODY, "1");
    assertThat(unavailable).hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
        .bodyJson().extractingPath("$.code")
        .isEqualTo("ENRICHMENT_PROVIDER_UNAVAILABLE");
    assertThat(unavailable).bodyText().doesNotContain(
        "provider secret detail", "token=secret", "cdn.example.com");
  }

  private void assertBadRequest(String body, String version) {
    assertThat(post(body, version)).hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson().extractingPath("$.code").isEqualTo("BAD_REQUEST");
  }

  private MvcTestResult post(String body, String version) {
    var request = mockMvc.post().uri("/api/enrichments/shoes/colors")
        .contentType(MediaType.APPLICATION_JSON)
        .content(body);
    if (version != null) {
      request.header("X-API-Version", version);
    }
    return request.exchange();
  }

  private ShoeColorEnrichmentResponse response() {
    return new ShoeColorEnrichmentResponse(
        ShoeColorStatus.COLORS_CONFIRMED,
        List.of(), List.of(), List.of(), List.of(), List.of(),
        false,
        new ExecutionMetadata(ExecutionRoute.PRIMARY));
  }
}
