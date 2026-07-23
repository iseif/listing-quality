package dev.iseif.listingquality.enrichment.service.book;

import dev.iseif.listingquality.enrichment.catalog.book.BookCatalogEvidenceLedger;
import dev.iseif.listingquality.enrichment.catalog.book.BookCatalogTools;
import dev.iseif.listingquality.enrichment.model.ExecutionRoute;
import dev.iseif.listingquality.enrichment.model.book.BookEnrichmentRequest;
import dev.iseif.listingquality.enrichment.model.book.BookField;
import dev.iseif.listingquality.enrichment.model.book.EnrichmentStatus;
import dev.iseif.listingquality.enrichment.model.book.GeneratedBookEnrichment;
import dev.iseif.listingquality.enrichment.observability.EnrichmentFeature;
import dev.iseif.listingquality.enrichment.observability.EnrichmentTelemetry;
import dev.iseif.listingquality.enrichment.service.book.exception.BookEnrichmentValidationFailure;
import dev.iseif.listingquality.enrichment.service.book.exception.InvalidBookEnrichmentResponseException;
import dev.iseif.listingquality.enrichment.service.execution.EnrichmentFailureClassifier;
import dev.iseif.listingquality.enrichment.service.execution.ModelExecutionException;
import dev.iseif.listingquality.enrichment.service.execution.ModelFailureCategory;
import dev.iseif.listingquality.enrichment.service.execution.ModelRoute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class FailoverBookEnrichmentGeneratorTest {

  @Mock
  private BookEnrichmentGenerator primaryGenerator;

  @Mock
  private BookEnrichmentGenerator fallbackGenerator;

  @Mock
  private ModelRoute primaryRoute;

  @Mock
  private ModelRoute fallbackRoute;

  @Mock
  private BookEnrichmentValidator validator;

  @Mock
  private BookCatalogTools tools;

  @Mock
  private EnrichmentTelemetry telemetry;

  private final BookCatalogEvidenceLedger ledger = new BookCatalogEvidenceLedger();
  private final BookEnrichmentRequest request = new BookEnrichmentRequest(
      "book-1", "Clean Code", null, null, Map.of());
  private final GeneratedBookEnrichment generated = new GeneratedBookEnrichment(
      List.of(), List.of(), List.of(), List.of());
  private final ValidatedBookEnrichment validated = new ValidatedBookEnrichment(
      EnrichmentStatus.NO_SAFE_PROPOSALS, List.of(), List.of(), List.of(), List.of());

  private FailoverBookEnrichmentGenerator failover;

  @BeforeEach
  void setUp() {
    given(primaryRoute.providerId()).willReturn("gemini");
    given(primaryRoute.call(any(), any())).willAnswer(invocation -> invoke(invocation.getArgument(0)));
    given(telemetry.observeRoute(any(), any(), any(), any()))
        .willAnswer(invocation -> invoke(invocation.getArgument(3)));
    failover = new FailoverBookEnrichmentGenerator(
        primaryGenerator,
        fallbackGenerator,
        primaryRoute,
        fallbackRoute,
        validator,
        new EnrichmentFailureClassifier(),
        Duration.ofSeconds(5),
        telemetry);
  }

  @Test
  void returnsPrimaryResultWithoutCallingFallback() {
    given(primaryGenerator.generate("same prompt", tools)).willReturn(generated);
    given(validator.validate(request, generated, ledger)).willReturn(validated);

    BookEnrichmentExecution result = failover.execute(request, "same prompt", tools, ledger);

    assertThat(result.route()).isEqualTo(ExecutionRoute.PRIMARY);
    assertThat(result.enrichment()).isSameAs(validated);
    verify(fallbackGenerator, never()).generate(any(), any());
  }

  @Test
  void fallsBackWithTheSamePromptAndRequestScopedTools() {
    enableFallbackRoute();
    ModelExecutionException primaryFailure = ModelExecutionException.eligible(
        "gemini", ModelFailureCategory.QUOTA_EXHAUSTED, new RuntimeException("detail"));
    given(primaryGenerator.generate("same prompt", tools)).willThrow(primaryFailure);
    given(fallbackGenerator.generate("same prompt", tools)).willReturn(generated);
    given(validator.validate(request, generated, ledger)).willReturn(validated);

    BookEnrichmentExecution result = failover.execute(request, "same prompt", tools, ledger);

    assertThat(result.route()).isEqualTo(ExecutionRoute.FALLBACK);
    verify(fallbackGenerator).generate("same prompt", tools);
    verify(telemetry).recordRouteFailure(
        EnrichmentFeature.BOOK, ExecutionRoute.PRIMARY, primaryFailure);
    verify(telemetry).recordFallback(EnrichmentFeature.BOOK, primaryFailure);
  }

  @Test
  void fallsBackWhenPrimaryOutputFailsGroundingValidation(CapturedOutput output) {
    enableFallbackRoute();
    given(primaryGenerator.generate("same prompt", tools)).willReturn(generated);
    given(fallbackGenerator.generate("same prompt", tools)).willReturn(generated);
    given(validator.validate(request, generated, ledger))
        .willThrow(new InvalidBookEnrichmentResponseException(
            BookEnrichmentValidationFailure.EVIDENCE_VALUE_MISMATCH,
            BookField.DESCRIPTION))
        .willReturn(validated);

    BookEnrichmentExecution result = failover.execute(request, "same prompt", tools, ledger);

    assertThat(result.route()).isEqualTo(ExecutionRoute.FALLBACK);
    assertThat(output).contains(
        "route=PRIMARY, failure=EVIDENCE_VALUE_MISMATCH, field=DESCRIPTION");
    verify(telemetry).recordFallback(
        org.mockito.ArgumentMatchers.eq(EnrichmentFeature.BOOK),
        org.mockito.ArgumentMatchers.any(InvalidBookEnrichmentResponseException.class));
  }

  @Test
  void doesNotFallbackForConfigurationFailure() {
    ModelExecutionException failure = ModelExecutionException.ineligible(
        "gemini", ModelFailureCategory.CONFIGURATION_ERROR, new RuntimeException("secret"));
    given(primaryGenerator.generate("same prompt", tools)).willThrow(failure);

    assertThatThrownBy(() -> failover.execute(request, "same prompt", tools, ledger))
        .isSameAs(failure);
    verify(fallbackGenerator, never()).generate(any(), any());
    verify(telemetry, never()).recordFallback(any(), any());
  }

  @Test
  void logsTheFallbackModelFailureBeforeCreatingTheTerminalError(CapturedOutput output) {
    enableFallbackRoute();
    given(primaryGenerator.generate("same prompt", tools)).willReturn(generated);
    given(validator.validate(request, generated, ledger))
        .willThrow(new InvalidBookEnrichmentResponseException(
            BookEnrichmentValidationFailure.EVIDENCE_REFERENCE_INVALID,
            BookField.TITLE));
    given(fallbackGenerator.generate("same prompt", tools)).willThrow(
        ModelExecutionException.eligible(
            "omlx", ModelFailureCategory.INVALID_MODEL_OUTPUT, new RuntimeException("secret")));

    assertThatThrownBy(() -> failover.execute(request, "same prompt", tools, ledger))
        .isInstanceOf(InvalidBookEnrichmentResponseException.class)
        .hasMessageNotContaining("secret");
    assertThat(output).contains(
        "route=FALLBACK, provider=omlx, category=INVALID_MODEL_OUTPUT")
        .doesNotContain("secret");
  }

  @SuppressWarnings("unchecked")
  private <T> T invoke(Object candidate) {
    return ((Supplier<T>) candidate).get();
  }

  private void enableFallbackRoute() {
    given(fallbackRoute.providerId()).willReturn("omlx");
    given(fallbackRoute.call(any(), any())).willAnswer(invocation -> invoke(invocation.getArgument(0)));
  }

}
