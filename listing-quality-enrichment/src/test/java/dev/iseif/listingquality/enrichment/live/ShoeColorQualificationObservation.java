package dev.iseif.listingquality.enrichment.live;

import dev.iseif.listingquality.enrichment.model.shoe.ShoeColor;
import dev.iseif.listingquality.enrichment.model.shoe.ShoeColorOutcome;
import dev.iseif.listingquality.enrichment.service.shoe.ShoeColorValidationFailure;

import java.util.Set;

record ShoeColorQualificationObservation(
    String caseId,
    ShoeColorOutcome expectedOutcome,
    ShoeColor expectedPrimary,
    Set<ShoeColor> expectedColors,
    Set<String> expectedIgnoredImageIds,
    Set<ShoeColor> forbiddenColors,
    boolean outsoleNegative,
    boolean backgroundPropNegative,
    ShoeColorOutcome actualOutcome,
    ShoeColor actualPrimary,
    Set<ShoeColor> actualColors,
    Set<String> actualIgnoredImageIds,
    boolean schemaFailure,
    ShoeColorValidationFailure validationFailure,
    long latencyMs) {

  ShoeColorQualificationObservation {
    expectedColors = Set.copyOf(expectedColors);
    expectedIgnoredImageIds = Set.copyOf(expectedIgnoredImageIds);
    forbiddenColors = Set.copyOf(forbiddenColors);
    actualColors = Set.copyOf(actualColors);
    actualIgnoredImageIds = Set.copyOf(actualIgnoredImageIds);
  }
}
