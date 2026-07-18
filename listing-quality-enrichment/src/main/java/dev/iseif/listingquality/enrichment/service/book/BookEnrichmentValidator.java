package dev.iseif.listingquality.enrichment.service.book;

import dev.iseif.listingquality.enrichment.catalog.book.BookCatalogEvidenceLedger;
import dev.iseif.listingquality.enrichment.model.book.*;
import dev.iseif.listingquality.enrichment.service.book.exception.BookEnrichmentValidationFailure;
import dev.iseif.listingquality.enrichment.service.book.exception.InvalidBookEnrichmentResponseException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.*;

/**
 * The second validation layer: schema validity proves the shape of the response, this proves that
 * every value the API is about to return came from a source the application can verify.
 *
 * <p>It owns the enrichment policy and delegates the mechanics. An item that fails on its own
 * evidence is quarantined so independently grounded items survive; a failure that breaks the
 * response contract rejects the whole route and can activate fallback.
 */
@Component
public final class BookEnrichmentValidator {

  private static final Logger log = LoggerFactory.getLogger(BookEnrichmentValidator.class);

  private static final Set<BookEnrichmentValidationFailure> QUARANTINABLE_FAILURES = EnumSet.of(
      BookEnrichmentValidationFailure.EVIDENCE_MISSING,
      BookEnrichmentValidationFailure.EVIDENCE_REFERENCE_INVALID,
      BookEnrichmentValidationFailure.EVIDENCE_FIELD_NOT_ALLOWED,
      BookEnrichmentValidationFailure.EVIDENCE_VALUE_MISMATCH,
      BookEnrichmentValidationFailure.DERIVED_VALUE_INVALID);

  private final Validator validator;
  private final BookValueMapper valueMapper;
  private final BookEvidenceResolver evidenceResolver;
  private final DerivedBookValueValidator derivedValueValidator;

  BookEnrichmentValidator(
      Validator validator,
      BookValueMapper valueMapper,
      BookEvidenceResolver evidenceResolver,
      DerivedBookValueValidator derivedValueValidator) {
    this.validator = validator;
    this.valueMapper = valueMapper;
    this.evidenceResolver = evidenceResolver;
    this.derivedValueValidator = derivedValueValidator;
  }

  public ValidatedBookEnrichment validate(
      BookEnrichmentRequest request,
      GeneratedBookEnrichment generated,
      BookCatalogEvidenceLedger ledger) {
    validateStructure(generated);
    Accumulator accumulator = new Accumulator(generated.unresolvedFields());

    for (GeneratedFieldProposal proposal : generated.proposals()) {
      try {
        accumulator.requireUniqueField(proposal.field());
        acceptProposal(request, proposal, accumulator, ledger);
      } catch (InvalidBookEnrichmentResponseException exception) {
        quarantine(exception, proposal.field(), accumulator, ledger);
      }
    }

    for (GeneratedFieldConflict conflict : generated.conflicts()) {
      try {
        accumulator.requireUniqueField(conflict.field());
        acceptConflict(request, conflict, accumulator, ledger);
      } catch (InvalidBookEnrichmentResponseException exception) {
        quarantine(exception, conflict.field(), accumulator, ledger);
      }
    }

    for (GeneratedDerivedBookAttribute attribute : generated.derivedAttributes()) {
      try {
        accumulator.requireUniqueDiscoveryField(attribute.field());
        acceptDerivedAttribute(request, attribute, accumulator, ledger);
      } catch (InvalidBookEnrichmentResponseException exception) {
        quarantine(exception, attribute.field(), accumulator, ledger);
      }
    }
    derivedValueValidator.validateAgeRange(accumulator.derivedAttributes());

    return accumulator.toValidatedEnrichment();
  }

  /**
   * A proposal fills a blank seller field. When the seller already has a different value it
   * becomes a conflict for review instead, except for the description, which the API may only
   * fill and never replace.
   */
  private void acceptProposal(
      BookEnrichmentRequest request,
      GeneratedFieldProposal proposal,
      Accumulator accumulator,
      BookCatalogEvidenceLedger ledger) {
    BookField field = proposal.field();
    JsonNode proposedValue = valueMapper.fromGenerated(proposal.proposedValue(), field);
    requireValidIsbn(field, proposedValue);
    List<Evidence> evidence = evidenceResolver.resolveFactual(
        request, field, proposedValue, proposal.evidence(), ledger);
    JsonNode sellerValue = valueMapper.sellerValue(request, field);
    if (sellerValue == null) {
      accumulator.addProposal(new FieldProposal(field, null, proposedValue, evidence));
      return;
    }
    if (valueMapper.equivalent(field, sellerValue, proposedValue)) {
      return;
    }
    requireNotDescription(field);
    accumulator.addConflict(conflict(field, sellerValue, proposedValue, evidence));
  }

  private void acceptConflict(
      BookEnrichmentRequest request,
      GeneratedFieldConflict conflict,
      Accumulator accumulator,
      BookCatalogEvidenceLedger ledger) {
    BookField field = conflict.field();
    requireNotDescription(field);
    JsonNode catalogValue = valueMapper.fromGenerated(conflict.catalogValue(), field);
    requireValidIsbn(field, catalogValue);
    JsonNode sellerValue = valueMapper.sellerValue(request, field);
    if (sellerValue == null || valueMapper.equivalent(field, sellerValue, catalogValue)) {
      throw invalid(BookEnrichmentValidationFailure.CONFLICT_INVALID, field);
    }
    List<Evidence> evidence = evidenceResolver.resolveFactual(
        request, field, catalogValue, conflict.evidence(), ledger);
    accumulator.addConflict(conflict(field, sellerValue, catalogValue, evidence));
  }

  private void acceptDerivedAttribute(
      BookEnrichmentRequest request,
      GeneratedDerivedBookAttribute attribute,
      Accumulator accumulator,
      BookCatalogEvidenceLedger ledger) {
    BookDiscoveryField field = attribute.field();
    JsonNode derivedValue = valueMapper.fromGenerated(attribute.value(), field);
    derivedValueValidator.validateValue(field, derivedValue);
    List<Evidence> evidence = evidenceResolver.resolveDerived(
        request, field, attribute.evidence(), ledger);
    requireSummaryCitesDescription(field, evidence);
    accumulator.addDerivedAttribute(new DerivedBookAttribute(
        field, derivedValue, DerivationType.INFERRED_FROM_EVIDENCE, evidence));
  }

  private void quarantine(
      InvalidBookEnrichmentResponseException exception,
      Enum<?> field,
      Accumulator accumulator,
      BookCatalogEvidenceLedger ledger) {
    if (!QUARANTINABLE_FAILURES.contains(exception.failure())) {
      throw exception;
    }
    if (field instanceof BookField bookField) {
      accumulator.addUnresolved(bookField);
    }
    ledger.warning(EnrichmentWarning.UNVERIFIABLE_MODEL_ITEM_REJECTED);
    log.warn(
        "Rejected unverifiable enrichment item: failure={}, field={}",
        exception.failure(), exception.field());
  }

  private void validateStructure(GeneratedBookEnrichment generated) {
    if (generated == null) {
      throw invalid(BookEnrichmentValidationFailure.STRUCTURE_INVALID, null);
    }
    Set<ConstraintViolation<GeneratedBookEnrichment>> violations = validator.validate(generated);
    if (!violations.isEmpty()) {
      throw invalid(BookEnrichmentValidationFailure.STRUCTURE_INVALID, null);
    }
  }

  private void requireValidIsbn(BookField field, JsonNode value) {
    if (!valueMapper.isValidIsbn(field, value)) {
      throw invalid(BookEnrichmentValidationFailure.ISBN_INVALID, field);
    }
  }

  private void requireNotDescription(BookField field) {
    if (field == BookField.DESCRIPTION) {
      throw invalid(BookEnrichmentValidationFailure.DESCRIPTION_POLICY_VIOLATION, field);
    }
  }

  private void requireSummaryCitesDescription(
      BookDiscoveryField field, List<Evidence> evidence) {
    boolean citesDescription = evidence.stream().anyMatch(
        item -> BookField.DESCRIPTION.externalName().equals(item.sourceField()));
    if (field == BookDiscoveryField.SHORT_SUMMARY && !citesDescription) {
      throw invalid(
          BookEnrichmentValidationFailure.SHORT_SUMMARY_DESCRIPTION_EVIDENCE_MISSING, field);
    }
  }

  private FieldConflict conflict(
      BookField field, JsonNode sellerValue, JsonNode catalogValue, List<Evidence> evidence) {
    return new FieldConflict(
        field, sellerValue, catalogValue, evidence, ConflictResolution.SELLER_REVIEW_REQUIRED);
  }

  private InvalidBookEnrichmentResponseException invalid(
      BookEnrichmentValidationFailure failure, Enum<?> field) {
    return new InvalidBookEnrichmentResponseException(failure, field);
  }

  /**
   * Collects the surviving items while enforcing that the model returns each field only once.
   */
  private final class Accumulator {

    private final Set<BookField> seenFields = EnumSet.noneOf(BookField.class);
    private final Set<BookDiscoveryField> seenDiscoveryFields =
        EnumSet.noneOf(BookDiscoveryField.class);
    private final List<FieldProposal> proposals = new ArrayList<>();
    private final List<FieldConflict> conflicts = new ArrayList<>();
    private final List<DerivedBookAttribute> derivedAttributes = new ArrayList<>();
    private final Set<BookField> unresolvedFields;

    private Accumulator(List<BookField> generatedUnresolvedFields) {
      this.unresolvedFields = new LinkedHashSet<>(generatedUnresolvedFields);
    }

    private void requireUniqueField(BookField field) {
      if (!seenFields.add(field)) {
        throw invalid(BookEnrichmentValidationFailure.DUPLICATE_FIELD, field);
      }
    }

    private void requireUniqueDiscoveryField(BookDiscoveryField field) {
      if (!seenDiscoveryFields.add(field)) {
        throw invalid(BookEnrichmentValidationFailure.DUPLICATE_FIELD, field);
      }
    }

    private void addProposal(FieldProposal proposal) {
      proposals.add(proposal);
    }

    private void addConflict(FieldConflict conflict) {
      conflicts.add(conflict);
    }

    private void addDerivedAttribute(DerivedBookAttribute attribute) {
      derivedAttributes.add(attribute);
    }

    private void addUnresolved(BookField field) {
      unresolvedFields.add(field);
    }

    private List<DerivedBookAttribute> derivedAttributes() {
      return derivedAttributes;
    }

    private ValidatedBookEnrichment toValidatedEnrichment() {
      return new ValidatedBookEnrichment(
          status(), proposals, conflicts, derivedAttributes, List.copyOf(unresolvedFields));
    }

    private EnrichmentStatus status() {
      if (!proposals.isEmpty() || !derivedAttributes.isEmpty()) {
        return EnrichmentStatus.PROPOSALS_AVAILABLE;
      }
      if (!conflicts.isEmpty() || !unresolvedFields.isEmpty()) {
        return EnrichmentStatus.NEEDS_SELLER_INPUT;
      }
      return EnrichmentStatus.NO_SAFE_PROPOSALS;
    }
  }
}
