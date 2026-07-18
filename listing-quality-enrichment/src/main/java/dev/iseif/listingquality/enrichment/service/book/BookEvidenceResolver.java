package dev.iseif.listingquality.enrichment.service.book;

import dev.iseif.listingquality.enrichment.catalog.book.BookCatalogEvidenceLedger;
import dev.iseif.listingquality.enrichment.catalog.book.BookCatalogRecord;
import dev.iseif.listingquality.enrichment.model.book.*;
import dev.iseif.listingquality.enrichment.service.book.exception.BookEnrichmentValidationFailure;
import dev.iseif.listingquality.enrichment.service.book.exception.InvalidBookEnrichmentResponseException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns the model's evidence citations into evidence the application has proved.
 *
 * <p>A factual citation must resolve to a record returned during this request whose cited field
 * equals the proposed value. A derived citation must resolve to a nonblank field the discovery
 * field is allowed to reason from, but its value is not compared for equality: an inferred theme
 * is not expected to appear literally in the description it was inferred from.
 */
@Component
final class BookEvidenceResolver {

  private final BookValueMapper valueMapper;

  BookEvidenceResolver(BookValueMapper valueMapper) {
    this.valueMapper = valueMapper;
  }

  /**
   * Resolves the citations behind a factual proposal or conflict, requiring value equality.
   */
  public List<Evidence> resolveFactual(
      BookEnrichmentRequest request,
      BookField field,
      JsonNode proposedValue,
      List<EvidenceReference> references,
      BookCatalogEvidenceLedger ledger) {
    List<Evidence> evidence = new ArrayList<>();
    for (EvidenceReference reference : references) {
      evidence.add(switch (reference.source()) {
        case GOOGLE_BOOKS -> factualCatalogEvidence(field, proposedValue, reference, ledger);
        case SELLER_INPUT -> factualSellerEvidence(request, field, proposedValue, reference);
      });
    }
    return requireNotEmpty(evidence, field);
  }

  /**
   * Resolves the citations behind a derived attribute, requiring an allowed nonblank source.
   */
  public List<Evidence> resolveDerived(
      BookEnrichmentRequest request,
      BookDiscoveryField discoveryField,
      List<EvidenceReference> references,
      BookCatalogEvidenceLedger ledger) {
    List<Evidence> evidence = new ArrayList<>();
    for (EvidenceReference reference : references) {
      evidence.add(switch (reference.source()) {
        case GOOGLE_BOOKS -> derivedCatalogEvidence(discoveryField, reference, ledger);
        case SELLER_INPUT -> derivedSellerEvidence(request, discoveryField, reference);
      });
    }
    return requireNotEmpty(evidence, discoveryField);
  }

  private Evidence factualCatalogEvidence(
      BookField field,
      JsonNode proposedValue,
      EvidenceReference reference,
      BookCatalogEvidenceLedger ledger) {
    CitedRecord cited = citedRecord(field, reference, ledger);
    BookField sourceField = catalogField(reference.sourceField(), field);
    if (sourceField != field) {
      throw invalid(BookEnrichmentValidationFailure.EVIDENCE_FIELD_NOT_ALLOWED, field);
    }
    JsonNode sourceValue = valueMapper.catalogValue(cited.catalogRecord(), sourceField);
    if (!valueMapper.equivalent(field, sourceValue, proposedValue)) {
      throw invalid(BookEnrichmentValidationFailure.EVIDENCE_VALUE_MISMATCH, field);
    }
    return catalogEvidence(cited, reference);
  }

  private Evidence factualSellerEvidence(
      BookEnrichmentRequest request,
      BookField field,
      JsonNode proposedValue,
      EvidenceReference reference) {
    JsonNode sourceValue = valueMapper.sellerSourceValue(request, reference.sourceField());
    if (!valueMapper.equivalent(field, sourceValue, proposedValue)) {
      throw invalid(BookEnrichmentValidationFailure.EVIDENCE_VALUE_MISMATCH, field);
    }
    return sellerEvidence(reference);
  }

  private Evidence derivedCatalogEvidence(
      BookDiscoveryField discoveryField,
      EvidenceReference reference,
      BookCatalogEvidenceLedger ledger) {
    CitedRecord cited = citedRecord(discoveryField, reference, ledger);
    BookField sourceField = catalogField(reference.sourceField(), discoveryField);
    if (!isAllowedDerivedSource(discoveryField, sourceField)
        || valueMapper.catalogValue(cited.catalogRecord(), sourceField) == null) {
      throw invalid(BookEnrichmentValidationFailure.EVIDENCE_FIELD_NOT_ALLOWED, discoveryField);
    }
    return catalogEvidence(cited, reference);
  }

  private Evidence derivedSellerEvidence(
      BookEnrichmentRequest request,
      BookDiscoveryField discoveryField,
      EvidenceReference reference) {
    BookField sourceField = BookField.byExternalName(reference.sourceField())
        .filter(field -> field == BookField.TITLE || field == BookField.DESCRIPTION)
        .orElseThrow(() -> invalid(
            BookEnrichmentValidationFailure.EVIDENCE_REFERENCE_INVALID, discoveryField));
    if (!isAllowedDerivedSource(discoveryField, sourceField)) {
      throw invalid(BookEnrichmentValidationFailure.EVIDENCE_FIELD_NOT_ALLOWED, discoveryField);
    }
    if (valueMapper.sellerSourceValue(request, reference.sourceField()) == null) {
      throw invalid(BookEnrichmentValidationFailure.EVIDENCE_REFERENCE_INVALID, discoveryField);
    }
    return sellerEvidence(reference);
  }

  /**
   * Proves that the cited record was actually returned to the model during this request.
   */
  private CitedRecord citedRecord(
      Enum<?> field, EvidenceReference reference, BookCatalogEvidenceLedger ledger) {
    if (reference.recordId() == null || reference.recordId().isBlank()) {
      throw invalid(BookEnrichmentValidationFailure.EVIDENCE_REFERENCE_INVALID, field);
    }
    BookCatalogRecord catalogRecord = ledger.findRecord(reference.recordId()).orElseThrow(
        () -> invalid(BookEnrichmentValidationFailure.EVIDENCE_REFERENCE_INVALID, field));
    MatchType matchType = ledger.matchType(reference.recordId()).orElseThrow(
        () -> invalid(BookEnrichmentValidationFailure.EVIDENCE_REFERENCE_INVALID, field));
    return new CitedRecord(catalogRecord, matchType);
  }

  private boolean isAllowedDerivedSource(
      BookDiscoveryField discoveryField, BookField sourceField) {
    return switch (discoveryField) {
      case BOOK_LENGTH_ESTIMATE ->
          sourceField == BookField.PAGE_COUNT || sourceField == BookField.DESCRIPTION;
      case KEYWORDS -> switch (sourceField) {
        case TITLE, SUBTITLE, AUTHORS, DESCRIPTION, CATEGORIES -> true;
        default -> false;
      };
      case GENRES, TARGET_AUDIENCE, MIN_AGE, MAX_AGE, TONE, THEMES, PACING,
          READING_DIFFICULTY, SHORT_SUMMARY -> switch (sourceField) {
        case TITLE, SUBTITLE, DESCRIPTION, CATEGORIES -> true;
        default -> false;
      };
    };
  }

  private BookField catalogField(String sourceField, Enum<?> field) {
    return BookField.byExternalName(sourceField).orElseThrow(
        () -> invalid(BookEnrichmentValidationFailure.EVIDENCE_REFERENCE_INVALID, field));
  }

  private Evidence catalogEvidence(CitedRecord cited, EvidenceReference reference) {
    return new Evidence(
        EvidenceSource.GOOGLE_BOOKS,
        cited.catalogRecord().recordId(),
        reference.sourceField(),
        cited.catalogRecord().sourceUrl(),
        cited.matchType());
  }

  private Evidence sellerEvidence(EvidenceReference reference) {
    return new Evidence(
        EvidenceSource.SELLER_INPUT, null, reference.sourceField(), null, MatchType.SELLER_EXPLICIT);
  }

  private List<Evidence> requireNotEmpty(List<Evidence> evidence, Enum<?> field) {
    if (evidence.isEmpty()) {
      throw invalid(BookEnrichmentValidationFailure.EVIDENCE_MISSING, field);
    }
    return List.copyOf(evidence);
  }

  private InvalidBookEnrichmentResponseException invalid(
      BookEnrichmentValidationFailure failure, Enum<?> field) {
    return new InvalidBookEnrichmentResponseException(failure, field);
  }

  private record CitedRecord(BookCatalogRecord catalogRecord, MatchType matchType) {
  }
}
