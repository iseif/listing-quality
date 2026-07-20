package dev.iseif.listingquality.enrichment.service.shoe;

import dev.iseif.listingquality.enrichment.media.ProductImage;
import dev.iseif.listingquality.enrichment.model.shoe.*;

import java.util.*;
import java.util.stream.Collectors;

public final class ShoeColorExtractionValidator {

  public ValidatedShoeColorExtraction validate(
      GeneratedShoeColorExtraction generated,
      List<ProductImage> images) {
    Set<String> imageIds = images.stream()
        .map(ProductImage::imageId)
        .collect(Collectors.toUnmodifiableSet());
    Map<String, ShoeImageAssessmentReason> assessments = validateAssessments(
        generated.imageAssessments(), imageIds);

    if (generated.outcome() == ShoeColorOutcome.INCONCLUSIVE) {
      if (!generated.colors().isEmpty()) {
        throw invalid(ShoeColorValidationFailure.OUTCOME_INCONSISTENT, null);
      }
      return new ValidatedShoeColorExtraction(
          generated.outcome(), List.of(), ignoredImages(generated.imageAssessments()));
    }

    validateColorCardinality(generated.colors());
    validateEvidence(generated.colors(), imageIds, assessments);

    List<ObservedShoeColor> colors = generated.colors().stream()
        .map(color -> new ObservedShoeColor(
            color.color(), color.role(), color.evidenceImageIds()))
        .sorted((left, right) -> left.role() == right.role()
            ? 0
            : left.role() == ShoeColorRole.PRIMARY ? -1 : 1)
        .toList();
    return new ValidatedShoeColorExtraction(
        generated.outcome(), colors, ignoredImages(generated.imageAssessments()));
  }

  private Map<String, ShoeImageAssessmentReason> validateAssessments(
      List<GeneratedShoeImageAssessment> generatedAssessments,
      Set<String> imageIds) {
    var assessments = new HashMap<String, ShoeImageAssessmentReason>();
    for (GeneratedShoeImageAssessment assessment : generatedAssessments) {
      if (!imageIds.contains(assessment.imageId())) {
        throw invalid(
            ShoeColorValidationFailure.IMAGE_ASSESSMENT_REFERENCE_INVALID,
            assessment.imageId());
      }
      if (assessments.putIfAbsent(assessment.imageId(), assessment.reason()) != null) {
        throw invalid(
            ShoeColorValidationFailure.IMAGE_ASSESSMENT_DUPLICATED,
            assessment.imageId());
      }
    }
    if (!assessments.keySet().equals(imageIds)) {
      String missing = imageIds.stream()
          .filter(imageId -> !assessments.containsKey(imageId))
          .findFirst()
          .orElse(null);
      throw invalid(ShoeColorValidationFailure.IMAGE_ASSESSMENT_MISSING, missing);
    }
    return Map.copyOf(assessments);
  }

  private void validateColorCardinality(List<GeneratedObservedShoeColor> colors) {
    long primaryCount = colors.stream()
        .filter(color -> color.role() == ShoeColorRole.PRIMARY)
        .count();
    if (primaryCount != 1) {
      throw invalid(ShoeColorValidationFailure.PRIMARY_COUNT_INVALID, null);
    }
    long additionalCount = colors.size() - primaryCount;
    if (additionalCount > 2) {
      throw invalid(ShoeColorValidationFailure.TOO_MANY_ADDITIONAL_COLORS, null);
    }
    Set<ShoeColor> distinctColors = new HashSet<>();
    if (colors.stream().anyMatch(color -> !distinctColors.add(color.color()))) {
      throw invalid(ShoeColorValidationFailure.DUPLICATE_COLOR, null);
    }
  }

  private void validateEvidence(
      List<GeneratedObservedShoeColor> colors,
      Set<String> imageIds,
      Map<String, ShoeImageAssessmentReason> assessments) {
    for (GeneratedObservedShoeColor color : colors) {
      if (color.evidenceImageIds().isEmpty()) {
        throw invalid(ShoeColorValidationFailure.EVIDENCE_MISSING, null);
      }
      Set<String> distinctEvidence = new HashSet<>(color.evidenceImageIds());
      if (distinctEvidence.size() != color.evidenceImageIds().size()) {
        throw invalid(
            ShoeColorValidationFailure.EVIDENCE_REFERENCE_INVALID,
            color.evidenceImageIds().getFirst());
      }
      for (String imageId : color.evidenceImageIds()) {
        if (!imageIds.contains(imageId)) {
          throw invalid(ShoeColorValidationFailure.EVIDENCE_REFERENCE_INVALID, imageId);
        }
        if (assessments.get(imageId) != ShoeImageAssessmentReason.RETAIL_EXTERIOR) {
          throw invalid(ShoeColorValidationFailure.IGNORED_IMAGE_USED_AS_EVIDENCE, imageId);
        }
      }
    }
  }

  private List<IgnoredShoeImage> ignoredImages(
      List<GeneratedShoeImageAssessment> assessments) {
    return assessments.stream()
        .filter(assessment -> assessment.reason() != ShoeImageAssessmentReason.RETAIL_EXTERIOR)
        .map(assessment -> new IgnoredShoeImage(assessment.imageId(), assessment.reason()))
        .toList();
  }

  private InvalidShoeColorResponseException invalid(
      ShoeColorValidationFailure failure,
      String imageId) {
    return new InvalidShoeColorResponseException(failure, imageId);
  }
}
