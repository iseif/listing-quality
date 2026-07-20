package dev.iseif.listingquality.enrichment.service.shoe;

import dev.iseif.listingquality.enrichment.model.ExecutionMetadata;
import dev.iseif.listingquality.enrichment.model.ExecutionRoute;
import dev.iseif.listingquality.enrichment.model.shoe.*;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ShoeColorComparisonPolicy {

  public ShoeColorEnrichmentResponse compare(
      ShoeColorEnrichmentRequest request,
      ValidatedShoeColorExtraction extraction,
      List<ShoeColorWarning> warnings,
      ExecutionRoute route) {
    if (extraction.outcome() == ShoeColorOutcome.INCONCLUSIVE) {
      return response(
          ShoeColorStatus.NEEDS_SELLER_INPUT,
          extraction,
          List.of(),
          List.of(),
          warnings,
          false,
          route);
    }

    List<ShoeColor> observed = extraction.observedColors().stream()
        .map(ObservedShoeColor::color)
        .toList();
    Set<ShoeColor> observedSet = new LinkedHashSet<>(observed);
    Set<ShoeColor> listingSet = new LinkedHashSet<>(request.listingColors());

    if (listingSet.equals(observedSet)) {
      return response(
          ShoeColorStatus.COLORS_CONFIRMED,
          extraction,
          List.of(),
          List.of(),
          warnings,
          false,
          route);
    }

    ShoeColor primary = extraction.observedColors().stream()
        .filter(color -> color.role() == ShoeColorRole.PRIMARY)
        .map(ObservedShoeColor::color)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException(
            "A conclusive extraction must carry exactly one primary color"));
    boolean conflict = !listingSet.isEmpty()
        && (!listingSet.contains(primary) || !observedSet.containsAll(listingSet));
    if (conflict) {
      return response(
          ShoeColorStatus.CONFLICTS_FOUND,
          extraction,
          List.of(),
          List.of(new ShoeColorConflict(
              ShoeField.COLORS, request.listingColors(), observed)),
          warnings,
          true,
          route);
    }

    return response(
        ShoeColorStatus.PROPOSALS_AVAILABLE,
        extraction,
        List.of(new ShoeColorProposal(
            ShoeField.COLORS, request.listingColors(), observed)),
        List.of(),
        warnings,
        true,
        route);
  }

  private ShoeColorEnrichmentResponse response(
      ShoeColorStatus status,
      ValidatedShoeColorExtraction extraction,
      List<ShoeColorProposal> proposals,
      List<ShoeColorConflict> conflicts,
      List<ShoeColorWarning> warnings,
      boolean requiresSellerApproval,
      ExecutionRoute route) {
    return new ShoeColorEnrichmentResponse(
        status,
        extraction.observedColors(),
        extraction.ignoredImages(),
        proposals,
        conflicts,
        warnings,
        requiresSellerApproval,
        new ExecutionMetadata(route));
  }
}
