package dev.iseif.listingquality.enrichment.service.shoe;

import dev.iseif.listingquality.enrichment.media.ProductImage;
import dev.iseif.listingquality.enrichment.model.ExecutionRoute;
import dev.iseif.listingquality.enrichment.model.shoe.GeneratedShoeColorExtraction;
import dev.iseif.listingquality.enrichment.model.shoe.ShoeColorOutcome;
import dev.iseif.listingquality.enrichment.observability.EnrichmentFeature;
import dev.iseif.listingquality.enrichment.observability.EnrichmentTelemetry;
import dev.iseif.listingquality.enrichment.service.execution.EnrichmentFailureClassifier;
import dev.iseif.listingquality.enrichment.service.execution.ModelExecutionException;
import dev.iseif.listingquality.enrichment.service.execution.ModelFailureCategory;
import dev.iseif.listingquality.enrichment.service.execution.ModelRoute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.MimeTypeUtils;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FailoverShoeColorGeneratorTest {

  @Mock
  private ShoeColorGenerator primary;

  @Mock
  private ShoeColorGenerator fallback;

  @Mock
  private ModelRoute primaryRoute;

  @Mock
  private ModelRoute fallbackRoute;

  @Mock
  private ShoeColorExtractionValidator validator;

  @Mock
  private EnrichmentTelemetry telemetry;

  private final String prompt = "same prompt";
  private final List<ProductImage> images = List.of(image("IMAGE_1"));
  private final GeneratedShoeColorExtraction generated = new GeneratedShoeColorExtraction(
      ShoeColorOutcome.COLORS_OBSERVED, List.of(), List.of());
  private final ValidatedShoeColorExtraction observed = new ValidatedShoeColorExtraction(
      ShoeColorOutcome.COLORS_OBSERVED, List.of(), List.of());
  private final ValidatedShoeColorExtraction inconclusive = new ValidatedShoeColorExtraction(
      ShoeColorOutcome.INCONCLUSIVE, List.of(), List.of());

  private FailoverShoeColorGenerator failoverGenerator;

  @BeforeEach
  void setUp() {
    given(primaryRoute.providerId()).willReturn("omlx");
    given(primaryRoute.call(any(), any())).willAnswer(invocation -> invoke(invocation.getArgument(0)));
    given(telemetry.observeRoute(any(), any(), any(), any()))
        .willAnswer(invocation -> invoke(invocation.getArgument(3)));
    failoverGenerator = new FailoverShoeColorGenerator(
        primary,
        fallback,
        primaryRoute,
        fallbackRoute,
        validator,
        new EnrichmentFailureClassifier(),
        Duration.ofSeconds(5),
        telemetry);
  }

  @Test
  void returnsAConclusivePrimaryResultWithoutFallback() {
    given(primary.generate(prompt, images)).willReturn(generated);
    given(validator.validate(generated, images)).willReturn(observed);

    ShoeColorExecution result = failoverGenerator.execute(prompt, images);

    assertThat(result.route()).isEqualTo(ExecutionRoute.PRIMARY);
    assertThat(result.extraction()).isSameAs(observed);
    verify(fallback, never()).generate(any(), any());
  }

  @Test
  void fallsBackWithTheSamePromptAndImageObjects() {
    enableFallbackRoute();
    given(primary.generate(prompt, images)).willThrow(
        ModelExecutionException.eligible(
            "omlx", ModelFailureCategory.PROVIDER_UNAVAILABLE, new RuntimeException("detail")));
    given(fallback.generate(prompt, images)).willReturn(generated);
    given(validator.validate(generated, images)).willReturn(observed);

    ShoeColorExecution result = failoverGenerator.execute(prompt, images);

    assertThat(result.route()).isEqualTo(ExecutionRoute.FALLBACK);
    verify(fallback).generate(same(prompt), same(images));
    verify(telemetry).recordFallback(
        org.mockito.ArgumentMatchers.eq(EnrichmentFeature.SHOE_COLOR),
        org.mockito.ArgumentMatchers.any(ModelExecutionException.class));
  }

  @Test
  void fallsBackForInvalidPrimaryOutput() {
    enableFallbackRoute();
    given(primary.generate(prompt, images)).willReturn(generated);
    given(fallback.generate(prompt, images)).willReturn(generated);
    given(validator.validate(generated, images))
        .willThrow(new InvalidShoeColorResponseException(
            ShoeColorValidationFailure.EVIDENCE_REFERENCE_INVALID, "IMAGE_9"))
        .willReturn(observed);

    assertThat(failoverGenerator.execute(prompt, images).route())
        .isEqualTo(ExecutionRoute.FALLBACK);
  }

  @Test
  void recordsOneFallbackWhenPrimaryOutputIsInconclusive() {
    enableFallbackRoute();
    given(primary.generate(prompt, images)).willReturn(generated);
    given(fallback.generate(prompt, images)).willReturn(generated);
    given(validator.validate(generated, images))
        .willReturn(inconclusive)
        .willReturn(observed);

    assertThat(failoverGenerator.execute(prompt, images).route())
        .isEqualTo(ExecutionRoute.FALLBACK);
    verify(telemetry).recordInconclusiveFallback(EnrichmentFeature.SHOE_COLOR);
  }

  @Test
  void returnsAnInconclusiveFallbackForJavaToMapToSellerInput() {
    enableFallbackRoute();
    given(primary.generate(prompt, images)).willReturn(generated);
    given(fallback.generate(prompt, images)).willReturn(generated);
    given(validator.validate(generated, images)).willReturn(inconclusive);

    ShoeColorExecution result = failoverGenerator.execute(prompt, images);

    assertThat(result.route()).isEqualTo(ExecutionRoute.FALLBACK);
    assertThat(result.extraction().outcome()).isEqualTo(ShoeColorOutcome.INCONCLUSIVE);
  }

  @Test
  void doesNotFallbackForAnIneligibleConfigurationFailure() {
    var failure = ModelExecutionException.ineligible(
        "omlx", ModelFailureCategory.CONFIGURATION_ERROR, new RuntimeException("secret"));
    given(primary.generate(prompt, images)).willThrow(failure);

    assertThatThrownBy(() -> failoverGenerator.execute(prompt, images)).isSameAs(failure);
    verify(fallback, never()).generate(any(), any());
    verify(telemetry, never()).recordFallback(any(), any());
  }

  @Test
  void preservesTheAvailabilityFailureWhenBothRoutesFail() {
    enableFallbackRoute();
    var primaryFailure = ModelExecutionException.eligible(
        "omlx", ModelFailureCategory.PROVIDER_UNAVAILABLE, new RuntimeException("primary"));
    given(primary.generate(prompt, images)).willThrow(primaryFailure);
    var fallbackFailure = ModelExecutionException.eligible(
        "gemini", ModelFailureCategory.INVALID_MODEL_OUTPUT, new RuntimeException("fallback"));
    given(fallback.generate(prompt, images)).willThrow(fallbackFailure);

    assertThatThrownBy(() -> failoverGenerator.execute(prompt, images))
        .isSameAs(primaryFailure)
        .hasMessage("AI model execution failed")
        .hasMessageNotContaining("primary")
        .hasMessageNotContaining("fallback");
  }

  @SuppressWarnings("unchecked")
  private <T> T invoke(Object candidate) {
    return ((Supplier<T>) candidate).get();
  }

  private void enableFallbackRoute() {
    given(fallbackRoute.providerId()).willReturn("gemini");
    given(fallbackRoute.call(any(), any())).willAnswer(
        invocation -> invoke(invocation.getArgument(0)));
  }

  private static ProductImage image(String id) {
    return new ProductImage(id, MimeTypeUtils.IMAGE_JPEG, new byte[] {1, 2, 3});
  }
}
