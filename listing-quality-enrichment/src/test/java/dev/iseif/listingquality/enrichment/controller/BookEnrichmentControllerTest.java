package dev.iseif.listingquality.enrichment.controller;

import dev.iseif.listingquality.enrichment.model.ExecutionMetadata;
import dev.iseif.listingquality.enrichment.model.ExecutionRoute;
import dev.iseif.listingquality.enrichment.model.book.BookEnrichmentResponse;
import dev.iseif.listingquality.enrichment.model.book.EnrichmentStatus;
import dev.iseif.listingquality.enrichment.service.book.BookEnrichmentService;
import dev.iseif.listingquality.enrichment.service.book.exception.InvalidBookEnrichmentResponseException;
import dev.iseif.listingquality.enrichment.service.execution.ModelExecutionException;
import dev.iseif.listingquality.enrichment.service.execution.ModelFailureCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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

@WebMvcTest(BookEnrichmentController.class)
@ExtendWith(OutputCaptureExtension.class)
class BookEnrichmentControllerTest {

  private static final String VALID_BODY = """
      {
        "listingId": "book-1",
        "title": "Clean Code",
        "description": "A used programming book",
        "attributes": { "author": "Robert C. Martin" }
      }
      """;

  @Autowired
  private MockMvcTester mockMvc;

  @MockitoBean
  private BookEnrichmentService service;

  @Test
  void returnsAResponseForTheVersionedEndpoint() {
    given(service.enrich(any())).willReturn(response());

    MvcTestResult result = post(VALID_BODY, "1");

    assertThat(result).hasStatusOk();
    assertThat(result).bodyJson().extractingPath("$.status")
        .isEqualTo("NO_SAFE_PROPOSALS");
    assertThat(result).bodyJson().extractingPath("$.execution.route").isEqualTo("PRIMARY");
  }

  @Test
  void rejectsInvalidInputAndUnsupportedVersions() {
    assertThat(post(VALID_BODY.replace("Clean Code", ""), null))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson().extractingPath("$.code").isEqualTo("BAD_REQUEST");
    assertThat(post(VALID_BODY, "999"))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson().extractingPath("$.code").isEqualTo("BAD_REQUEST");
  }

  @Test
  void returnsConciseAvailabilityErrorsWithoutProviderDetails() {
    given(service.enrich(any())).willThrow(ModelExecutionException.eligible(
        "gemini",
        ModelFailureCategory.QUOTA_EXHAUSTED,
        new RuntimeException("secret-key seller title raw provider payload")));

    MvcTestResult result = post(VALID_BODY, null);

    assertThat(result).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(result).bodyJson().extractingPath("$.code")
        .isEqualTo("ENRICHMENT_PROVIDER_UNAVAILABLE");
    assertThat(result).bodyText().doesNotContain(
        "secret-key", "seller title", "raw provider payload", "stackTrace", "trace");
  }

  @Test
  void distinguishesInvalidOutputFromConfigurationFailure() {
    given(service.enrich(any())).willThrow(new InvalidBookEnrichmentResponseException());
    assertThat(post(VALID_BODY, null)).hasStatus(HttpStatus.BAD_GATEWAY)
        .bodyJson().extractingPath("$.code")
        .isEqualTo("ENRICHMENT_RESPONSE_INVALID");

    willThrow(ModelExecutionException.ineligible(
        "gemini", ModelFailureCategory.CONFIGURATION_ERROR, new RuntimeException("api key")))
        .given(service).enrich(any());
    assertThat(post(VALID_BODY, null)).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
        .bodyJson().extractingPath("$.code")
        .isEqualTo("ENRICHMENT_CONFIGURATION_ERROR");
  }

  @Test
  void logsSafeGroundingDiagnosticsWithoutChangingTheHttpError(CapturedOutput output) {
    given(service.enrich(any())).willThrow(new InvalidBookEnrichmentResponseException());

    MvcTestResult result = post(VALID_BODY, null);

    assertThat(result).hasStatus(HttpStatus.BAD_GATEWAY)
        .bodyJson().extractingPath("$.code")
        .isEqualTo("ENRICHMENT_RESPONSE_INVALID");
    assertThat(output).contains("failure=UNCLASSIFIED, field=UNKNOWN")
        .doesNotContain(VALID_BODY, "Clean Code", "Robert C. Martin");
  }

  @Test
  void mapsFrameworkErrorsToProblemDetails() {
    assertThat(mockMvc.get().uri("/api/enrichments/books").exchange())
        .hasStatus(HttpStatus.METHOD_NOT_ALLOWED)
        .bodyJson().extractingPath("$.code").isEqualTo("METHOD_NOT_ALLOWED");
    assertThat(mockMvc.post().uri("/api/enrichments/books")
        .contentType(MediaType.TEXT_PLAIN).content("book").exchange())
        .hasStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
        .bodyJson().extractingPath("$.code").isEqualTo("UNSUPPORTED_MEDIA_TYPE");
  }

  private MvcTestResult post(String body, String version) {
    var request = mockMvc.post().uri("/api/enrichments/books")
        .contentType(MediaType.APPLICATION_JSON)
        .content(body);
    if (version != null) {
      request.header("X-API-Version", version);
    }
    return request.exchange();
  }

  private BookEnrichmentResponse response() {
    return new BookEnrichmentResponse(
        EnrichmentStatus.NO_SAFE_PROPOSALS,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        false,
        new ExecutionMetadata(ExecutionRoute.PRIMARY));
  }
}
