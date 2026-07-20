package dev.iseif.listingquality.enrichment.service.shoe;

import dev.iseif.listingquality.enrichment.model.ExecutionRoute;
import dev.iseif.listingquality.enrichment.model.shoe.*;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

import static dev.iseif.listingquality.enrichment.model.shoe.ShoeColor.*;
import static dev.iseif.listingquality.enrichment.model.shoe.ShoeColorRole.ADDITIONAL;
import static dev.iseif.listingquality.enrichment.model.shoe.ShoeColorRole.PRIMARY;
import static org.assertj.core.api.Assertions.assertThat;

class ShoeColorComparisonPolicyTest {

  private final ShoeColorComparisonPolicy policy = new ShoeColorComparisonPolicy();

  @Test
  void proposesBrownFromTheVisibleOutsoleEdgeButIgnoresTheBottomView() {
    var validated = observed(
        primary(GREEN, "IMAGE_1"),
        additional(WHITE, "IMAGE_1"),
        additional(BROWN, "IMAGE_1"),
        ignored("IMAGE_2", ShoeImageAssessmentReason.OUTSOLE_ONLY));

    var response = policy.compare(
        request(List.of(GREEN, WHITE)), validated, List.of(), ExecutionRoute.PRIMARY);

    assertThat(response.status()).isEqualTo(ShoeColorStatus.PROPOSALS_AVAILABLE);
    assertThat(response.proposals()).singleElement()
        .satisfies(proposal -> assertThat(proposal.proposedValue())
            .containsExactly(GREEN, WHITE, BROWN));
    assertThat(response.ignoredImages()).singleElement()
        .satisfies(image -> assertThat(image.imageId()).isEqualTo("IMAGE_2"));
    assertThat(response.requiresSellerApproval()).isTrue();
  }

  @Test
  void confirmsAnExactSetRegardlessOfListingOrder() {
    var response = policy.compare(
        request(List.of(WHITE, GREEN)),
        observed(primary(GREEN, "IMAGE_1"), additional(WHITE, "IMAGE_1")),
        List.of(),
        ExecutionRoute.PRIMARY);

    assertThat(response.status()).isEqualTo(ShoeColorStatus.COLORS_CONFIRMED);
    assertThat(response.proposals()).isEmpty();
    assertThat(response.conflicts()).isEmpty();
    assertThat(response.requiresSellerApproval()).isFalse();
  }

  @Test
  void proposesTheObservedPaletteWhenListingColorsAreEmpty() {
    var response = policy.compare(
        request(List.of()),
        observed(primary(GREEN, "IMAGE_1"), additional(WHITE, "IMAGE_1")),
        List.of(),
        ExecutionRoute.FALLBACK);

    assertThat(response.status()).isEqualTo(ShoeColorStatus.PROPOSALS_AVAILABLE);
    assertThat(response.proposals()).singleElement()
        .satisfies(proposal -> {
          assertThat(proposal.currentValue()).isEmpty();
          assertThat(proposal.proposedValue()).containsExactly(GREEN, WHITE);
        });
    assertThat(response.execution().route()).isEqualTo(ExecutionRoute.FALLBACK);
  }

  @Test
  void reportsConflictsForAContradictoryPrimaryOrUnsupportedListingColor() {
    for (List<ShoeColor> listing : List.of(
        List.of(ShoeColor.RED, WHITE),
        List.of(GREEN, WHITE, ShoeColor.RED))) {
      var response = policy.compare(
          request(listing),
          observed(primary(GREEN, "IMAGE_1"), additional(WHITE, "IMAGE_1")),
          List.of(),
          ExecutionRoute.PRIMARY);

      assertThat(response.status()).isEqualTo(ShoeColorStatus.CONFLICTS_FOUND);
      assertThat(response.conflicts()).hasSize(1);
      assertThat(response.requiresSellerApproval()).isTrue();
    }
  }

  @Test
  void returnsNeedsSellerInputForInconclusiveOutputAndPreservesWarnings() {
    var validated = new ValidatedShoeColorExtraction(
        ShoeColorOutcome.INCONCLUSIVE,
        List.of(),
        List.of(ignored("IMAGE_1", ShoeImageAssessmentReason.UNUSABLE_IMAGE)));

    var response = policy.compare(
        request(List.of(GREEN)),
        validated,
        List.of(ShoeColorWarning.IMAGE_UNAVAILABLE),
        ExecutionRoute.FALLBACK);

    assertThat(response.status()).isEqualTo(ShoeColorStatus.NEEDS_SELLER_INPUT);
    assertThat(response.warnings()).containsExactly(ShoeColorWarning.IMAGE_UNAVAILABLE);
    assertThat(response.requiresSellerApproval()).isFalse();
  }

  private ValidatedShoeColorExtraction observed(Object... values) {
    List<ObservedShoeColor> colors = Arrays.stream(values)
        .filter(ObservedShoeColor.class::isInstance)
        .map(ObservedShoeColor.class::cast)
        .toList();
    List<IgnoredShoeImage> ignored = Arrays.stream(values)
        .filter(IgnoredShoeImage.class::isInstance)
        .map(IgnoredShoeImage.class::cast)
        .toList();
    return new ValidatedShoeColorExtraction(ShoeColorOutcome.COLORS_OBSERVED, colors, ignored);
  }

  private ObservedShoeColor primary(ShoeColor color, String imageId) {
    return new ObservedShoeColor(color, PRIMARY, List.of(imageId));
  }

  private ObservedShoeColor additional(ShoeColor color, String imageId) {
    return new ObservedShoeColor(color, ADDITIONAL, List.of(imageId));
  }

  private IgnoredShoeImage ignored(String imageId, ShoeImageAssessmentReason reason) {
    return new IgnoredShoeImage(imageId, reason);
  }

  private ShoeColorEnrichmentRequest request(List<ShoeColor> listingColors) {
    return new ShoeColorEnrichmentRequest(
        "shoe-123", listingColors, List.of(URI.create("https://cdn.example.com/shoe.jpg")));
  }
}
