package dev.iseif.listingquality.enrichment.service.book;

import dev.iseif.listingquality.enrichment.model.ExecutionMetadata;
import dev.iseif.listingquality.enrichment.model.ExecutionRoute;
import dev.iseif.listingquality.enrichment.model.book.BookEnrichmentResponse;
import dev.iseif.listingquality.enrichment.model.book.EnrichmentWarning;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

public record BookEnrichmentExecution(
    ValidatedBookEnrichment enrichment,
    ExecutionRoute route) {

  public BookEnrichmentResponse toResponse(Set<EnrichmentWarning> warnings) {
    List<EnrichmentWarning> orderedWarnings = warnings.stream()
        .sorted(Comparator.naturalOrder())
        .toList();
    boolean requiresSellerApproval = !enrichment.proposals().isEmpty()
        || !enrichment.conflicts().isEmpty()
        || !enrichment.derivedAttributes().isEmpty();
    return new BookEnrichmentResponse(
        enrichment.status(),
        enrichment.proposals(),
        enrichment.conflicts(),
        enrichment.derivedAttributes(),
        enrichment.unresolvedFields(),
        orderedWarnings,
        requiresSellerApproval,
        new ExecutionMetadata(route));
  }
}
