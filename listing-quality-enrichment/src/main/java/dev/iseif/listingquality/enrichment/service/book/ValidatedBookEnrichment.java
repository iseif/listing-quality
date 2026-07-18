package dev.iseif.listingquality.enrichment.service.book;

import dev.iseif.listingquality.enrichment.model.book.*;

import java.util.List;

public record ValidatedBookEnrichment(
    EnrichmentStatus status,
    List<FieldProposal> proposals,
    List<FieldConflict> conflicts,
    List<DerivedBookAttribute> derivedAttributes,
    List<BookField> unresolvedFields) {

  public ValidatedBookEnrichment {
    proposals = List.copyOf(proposals);
    conflicts = List.copyOf(conflicts);
    derivedAttributes = List.copyOf(derivedAttributes);
    unresolvedFields = List.copyOf(unresolvedFields);
  }
}
