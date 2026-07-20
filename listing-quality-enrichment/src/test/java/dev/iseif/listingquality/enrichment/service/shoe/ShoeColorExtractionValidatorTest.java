package dev.iseif.listingquality.enrichment.service.shoe;

import dev.iseif.listingquality.enrichment.media.ProductImage;
import dev.iseif.listingquality.enrichment.model.shoe.*;
import org.junit.jupiter.api.Test;
import org.springframework.util.MimeTypeUtils;

import java.util.List;

import static dev.iseif.listingquality.enrichment.model.shoe.ShoeColor.*;
import static dev.iseif.listingquality.enrichment.model.shoe.ShoeColorRole.ADDITIONAL;
import static dev.iseif.listingquality.enrichment.model.shoe.ShoeColorRole.PRIMARY;
import static dev.iseif.listingquality.enrichment.service.shoe.ShoeColorValidationFailure.IGNORED_IMAGE_USED_AS_EVIDENCE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShoeColorExtractionValidatorTest {

  private final ShoeColorExtractionValidator validator = new ShoeColorExtractionValidator();

  @Test
  void acceptsOnePrimaryAndRetailFacingAdditionalColors() {
    var generated = extraction(
        List.of(
            observed(GREEN, PRIMARY, "IMAGE_1"),
            observed(WHITE, ADDITIONAL, "IMAGE_1"),
            observed(BROWN, ADDITIONAL, "IMAGE_1")),
        List.of(
            assessment("IMAGE_1", ShoeImageAssessmentReason.RETAIL_EXTERIOR),
            assessment("IMAGE_2", ShoeImageAssessmentReason.OUTSOLE_ONLY)));

    ValidatedShoeColorExtraction validated = validator.validate(
        generated, List.of(image("IMAGE_1"), image("IMAGE_2")));

    assertThat(validated.observedColors()).extracting(color -> color.color())
        .containsExactly(GREEN, WHITE, BROWN);
    assertThat(validated.ignoredImages()).singleElement()
        .satisfies(image -> {
          assertThat(image.imageId()).isEqualTo("IMAGE_2");
          assertThat(image.reason()).isEqualTo(ShoeImageAssessmentReason.OUTSOLE_ONLY);
        });
  }

  @Test
  void rejectsEvidenceFromAnOutsoleOnlyImage() {
    var generated = extraction(
        List.of(observed(BROWN, PRIMARY, "IMAGE_2")),
        List.of(assessment("IMAGE_2", ShoeImageAssessmentReason.OUTSOLE_ONLY)));

    assertThatThrownBy(() -> validator.validate(generated, List.of(image("IMAGE_2"))))
        .isInstanceOfSatisfying(InvalidShoeColorResponseException.class, failure -> {
          assertThat(failure.failure()).isEqualTo(IGNORED_IMAGE_USED_AS_EVIDENCE);
          assertThat(failure.imageId()).isEqualTo("IMAGE_2");
          assertThat(failure.getMessage()).isEqualTo("Shoe color output failed validation");
        });
  }

  @Test
  void rejectsInvalidColorCardinalityAndDuplicates() {
    assertFailure(
        extraction(
            List.of(observed(GREEN, ADDITIONAL, "IMAGE_1")),
            List.of(assessment("IMAGE_1", ShoeImageAssessmentReason.RETAIL_EXTERIOR))),
        ShoeColorValidationFailure.PRIMARY_COUNT_INVALID);
    assertFailure(
        extraction(
            List.of(
                observed(GREEN, PRIMARY, "IMAGE_1"),
                observed(GREEN, ADDITIONAL, "IMAGE_1")),
            List.of(assessment("IMAGE_1", ShoeImageAssessmentReason.RETAIL_EXTERIOR))),
        ShoeColorValidationFailure.DUPLICATE_COLOR);
    assertFailure(
        extraction(
            List.of(
                observed(GREEN, PRIMARY, "IMAGE_1"),
                observed(WHITE, ADDITIONAL, "IMAGE_1"),
                observed(BROWN, ADDITIONAL, "IMAGE_1"),
                observed(ShoeColor.BLACK, ADDITIONAL, "IMAGE_1")),
            List.of(assessment("IMAGE_1", ShoeImageAssessmentReason.RETAIL_EXTERIOR))),
        ShoeColorValidationFailure.TOO_MANY_ADDITIONAL_COLORS);
  }

  @Test
  void rejectsMissingUnknownAndDuplicateImageEvidence() {
    assertFailure(
        extraction(
            List.of(observed(GREEN, PRIMARY)),
            List.of(assessment("IMAGE_1", ShoeImageAssessmentReason.RETAIL_EXTERIOR))),
        ShoeColorValidationFailure.EVIDENCE_MISSING);
    assertFailure(
        extraction(
            List.of(observed(GREEN, PRIMARY, "IMAGE_9")),
            List.of(assessment("IMAGE_1", ShoeImageAssessmentReason.RETAIL_EXTERIOR))),
        ShoeColorValidationFailure.EVIDENCE_REFERENCE_INVALID);
    assertFailure(
        extraction(
            List.of(observed(GREEN, PRIMARY, "IMAGE_1", "IMAGE_1")),
            List.of(assessment("IMAGE_1", ShoeImageAssessmentReason.RETAIL_EXTERIOR))),
        ShoeColorValidationFailure.EVIDENCE_REFERENCE_INVALID);
  }

  @Test
  void requiresEveryInputImageToBeAssessedExactlyOnce() {
    assertFailure(
        extraction(
            List.of(observed(GREEN, PRIMARY, "IMAGE_1")),
            List.of(assessment("IMAGE_1", ShoeImageAssessmentReason.RETAIL_EXTERIOR))),
        List.of(image("IMAGE_1"), image("IMAGE_2")),
        ShoeColorValidationFailure.IMAGE_ASSESSMENT_MISSING);
    assertFailure(
        extraction(
            List.of(observed(GREEN, PRIMARY, "IMAGE_1")),
            List.of(
                assessment("IMAGE_1", ShoeImageAssessmentReason.RETAIL_EXTERIOR),
                assessment("IMAGE_1", ShoeImageAssessmentReason.RETAIL_EXTERIOR))),
        ShoeColorValidationFailure.IMAGE_ASSESSMENT_DUPLICATED);
  }

  @Test
  void inconclusiveOutputCannotContainColors() {
    var generated = new GeneratedShoeColorExtraction(
        ShoeColorOutcome.INCONCLUSIVE,
        List.of(observed(GREEN, PRIMARY, "IMAGE_1")),
        List.of(assessment("IMAGE_1", ShoeImageAssessmentReason.UNUSABLE_IMAGE)));

    assertFailure(generated, ShoeColorValidationFailure.OUTCOME_INCONSISTENT);
  }

  private void assertFailure(
      GeneratedShoeColorExtraction generated,
      ShoeColorValidationFailure expected) {
    assertFailure(generated, List.of(image("IMAGE_1")), expected);
  }

  private void assertFailure(
      GeneratedShoeColorExtraction generated,
      List<ProductImage> images,
      ShoeColorValidationFailure expected) {
    assertThatThrownBy(() -> validator.validate(generated, images))
        .isInstanceOfSatisfying(InvalidShoeColorResponseException.class,
            failure -> assertThat(failure.failure()).isEqualTo(expected));
  }

  private GeneratedShoeColorExtraction extraction(
      List<GeneratedObservedShoeColor> colors,
      List<GeneratedShoeImageAssessment> assessments) {
    return new GeneratedShoeColorExtraction(
        ShoeColorOutcome.COLORS_OBSERVED, colors, assessments);
  }

  private GeneratedObservedShoeColor observed(
      ShoeColor color,
      ShoeColorRole role,
      String... imageIds) {
    return new GeneratedObservedShoeColor(color, role, List.of(imageIds));
  }

  private GeneratedShoeImageAssessment assessment(
      String imageId,
      ShoeImageAssessmentReason reason) {
    return new GeneratedShoeImageAssessment(imageId, reason);
  }

  private ProductImage image(String id) {
    return new ProductImage(id, MimeTypeUtils.IMAGE_JPEG, new byte[] {1, 2, 3});
  }
}
