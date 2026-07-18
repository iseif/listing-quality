package dev.iseif.listingquality.enrichment.service.book;

import dev.iseif.listingquality.enrichment.catalog.book.BookCatalogEvidenceLedger;
import dev.iseif.listingquality.enrichment.catalog.book.BookCatalogRecord;
import dev.iseif.listingquality.enrichment.catalog.book.CatalogLookupResult;
import dev.iseif.listingquality.enrichment.model.book.*;
import dev.iseif.listingquality.enrichment.service.book.exception.InvalidBookEnrichmentResponseException;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.StringNode;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookEnrichmentValidatorTest {

  private BookEnrichmentValidator validator;

  @BeforeEach
  void setUp() {
    BookValueMapper valueMapper = new BookValueMapper(JsonMapper.shared());
    validator = new BookEnrichmentValidator(
        Validation.buildDefaultValidatorFactory().getValidator(),
        valueMapper,
        new BookEvidenceResolver(valueMapper),
        new DerivedBookValueValidator());
  }

  @Test
  void acceptsAProposalThatMatchesExactIsbnCatalogEvidence() {
    BookCatalogEvidenceLedger ledger = exactLedger(catalogRecord("Prentice Hall", "9780132350884"));
    GeneratedBookEnrichment generated = generated(proposal(
        BookField.PUBLISHER,
        text("Prentice Hall"),
        new EvidenceReference(EvidenceSource.GOOGLE_BOOKS, "volume-1", "publisher")));

    ValidatedBookEnrichment result = validator.validate(request(Map.of()), generated, ledger);

    assertThat(result.status()).isEqualTo(EnrichmentStatus.PROPOSALS_AVAILABLE);
    assertThat(result.proposals()).hasSize(1);
    assertThat(result.proposals().getFirst().evidence().getFirst().matchType())
        .isEqualTo(MatchType.EXACT_ISBN);
  }

  @Test
  void quarantinesAnUnverifiableFieldAndKeepsAnIndependentlyGroundedField() {
    BookCatalogEvidenceLedger ledger = exactLedger(catalogRecord("Prentice Hall", "9780132350884"));
    GeneratedFieldProposal invalidTitle = proposal(
        BookField.TITLE,
        text("Clean Code by Robert C. Martin"),
        new EvidenceReference(EvidenceSource.GOOGLE_BOOKS, "volume-1", "title"));
    GeneratedFieldProposal validIsbn = proposal(
        BookField.ISBN13,
        text("9780132350884"),
        new EvidenceReference(EvidenceSource.GOOGLE_BOOKS, "volume-1", "isbn13"));
    GeneratedBookEnrichment generated = new GeneratedBookEnrichment(
        List.of(invalidTitle, validIsbn), List.of(), List.of(), List.of());
    BookEnrichmentRequest request = new BookEnrichmentRequest(
        "book-123", "Clean Code by Robert C. Martin", null, null, Map.of());

    ValidatedBookEnrichment result = validator.validate(request, generated, ledger);

    assertThat(result.proposals()).singleElement().satisfies(proposal ->
        assertThat(proposal.field()).isEqualTo(BookField.ISBN13));
    assertThat(result.unresolvedFields()).contains(BookField.TITLE);
    assertThat(ledger.warnings().toString()).contains("UNVERIFIABLE_MODEL_ITEM_REJECTED");
  }

  @Test
  void quarantinesMissingAndAmbiguousCatalogEvidence() {
    GeneratedBookEnrichment missing = generated(proposal(
        BookField.PUBLISHER,
        text("Prentice Hall"),
        new EvidenceReference(EvidenceSource.GOOGLE_BOOKS, "missing", "publisher")));
    BookCatalogEvidenceLedger missingLedger = new BookCatalogEvidenceLedger();

    ValidatedBookEnrichment missingResult = validator.validate(
        request(Map.of()), missing, missingLedger);

    assertThat(missingResult.proposals()).isEmpty();
    assertThat(missingResult.unresolvedFields()).contains(BookField.PUBLISHER);
    assertThat(missingLedger.warnings().toString())
        .contains("UNVERIFIABLE_MODEL_ITEM_REJECTED");

    BookCatalogEvidenceLedger ambiguousLedger = new BookCatalogEvidenceLedger();
    ambiguousLedger.recordLookup(
        "title:clean code|author:robert martin",
        CatalogLookupResult.ambiguous(List.of(catalogRecord("Prentice Hall", "9780132350884"))));
    GeneratedBookEnrichment ambiguous = generated(proposal(
        BookField.PUBLISHER,
        text("Prentice Hall"),
        new EvidenceReference(EvidenceSource.GOOGLE_BOOKS, "volume-1", "publisher")));

    ValidatedBookEnrichment ambiguousResult = validator.validate(
        request(Map.of()), ambiguous, ambiguousLedger);

    assertThat(ambiguousResult.proposals()).isEmpty();
    assertThat(ambiguousResult.unresolvedFields()).contains(BookField.PUBLISHER);
    assertThat(ambiguousLedger.warnings().toString())
        .contains("UNVERIFIABLE_MODEL_ITEM_REJECTED");
  }

  @Test
  void convertsAProposalIntoAConflictWhenSellerDataAlreadyDiffers() {
    BookCatalogEvidenceLedger ledger = exactLedger(catalogRecord("Prentice Hall", "9780132350884"));
    GeneratedBookEnrichment generated = generated(proposal(
        BookField.PUBLISHER,
        text("Prentice Hall"),
        new EvidenceReference(EvidenceSource.GOOGLE_BOOKS, "volume-1", "publisher")));

    ValidatedBookEnrichment result = validator.validate(
        request(Map.of("publisher", "Seller Publisher")), generated, ledger);

    assertThat(result.proposals()).isEmpty();
    assertThat(result.conflicts()).singleElement().satisfies(conflict -> {
      assertThat(conflict.sellerValue().asString()).isEqualTo("Seller Publisher");
      assertThat(conflict.catalogValue().asString()).isEqualTo("Prentice Hall");
      assertThat(conflict.resolution()).isEqualTo(ConflictResolution.SELLER_REVIEW_REQUIRED);
    });
    assertThat(result.status()).isEqualTo(EnrichmentStatus.NEEDS_SELLER_INPUT);
  }

  @Test
  void rejectsAnInvalidIsbnEvenWhenTheCatalogReturnedIt() {
    BookCatalogEvidenceLedger ledger = exactLedger(catalogRecord("Prentice Hall", "9780132350885"));
    GeneratedBookEnrichment generated = generated(proposal(
        BookField.ISBN13,
        text("9780132350885"),
        new EvidenceReference(EvidenceSource.GOOGLE_BOOKS, "volume-1", "isbn13")));

    assertInvalid(() -> validator.validate(request(Map.of()), generated, ledger));
  }

  @Test
  void rejectsDuplicateProposalFields() {
    BookCatalogEvidenceLedger ledger = exactLedger(catalogRecord("Prentice Hall", "9780132350884"));
    GeneratedFieldProposal proposal = proposal(
        BookField.PUBLISHER,
        text("Prentice Hall"),
        new EvidenceReference(EvidenceSource.GOOGLE_BOOKS, "volume-1", "publisher"));
    GeneratedBookEnrichment generated = new GeneratedBookEnrichment(
        List.of(proposal, proposal), List.of(), List.of(), List.of());

    assertInvalid(() -> validator.validate(request(Map.of()), generated, ledger));
  }

  @Test
  void proposesCatalogDescriptionOnlyWhenSellerDescriptionIsMissing() {
    BookCatalogEvidenceLedger ledger = exactLedger(catalogRecord("Prentice Hall", "9780132350884"));
    GeneratedBookEnrichment generated = generated(proposal(
        BookField.DESCRIPTION,
        text("A handbook about writing maintainable software."),
        new EvidenceReference(EvidenceSource.GOOGLE_BOOKS, "volume-1", "description")));
    BookEnrichmentRequest request = new BookEnrichmentRequest(
        "book-123", "Clean Code", null, null, Map.of());

    ValidatedBookEnrichment result = validator.validate(request, generated, ledger);

    assertThat(result.proposals()).singleElement().satisfies(proposal -> {
      assertThat(proposal.field()).isEqualTo(BookField.DESCRIPTION);
      assertThat(proposal.proposedValue().asString())
          .isEqualTo("A handbook about writing maintainable software.");
    });
  }

  @Test
  void rejectsAProposalThatWouldOverwriteSellerDescription() {
    BookCatalogEvidenceLedger ledger = exactLedger(catalogRecord("Prentice Hall", "9780132350884"));
    GeneratedBookEnrichment generated = generated(proposal(
        BookField.DESCRIPTION,
        text("A handbook about writing maintainable software."),
        new EvidenceReference(EvidenceSource.GOOGLE_BOOKS, "volume-1", "description")));

    assertInvalid(() -> validator.validate(request(Map.of()), generated, ledger));
  }

  @Test
  void acceptsDiscoveryThemesDerivedFromCatalogDescriptionEvidence() {
    BookCatalogEvidenceLedger ledger = exactLedger(catalogRecord("Prentice Hall", "9780132350884"));
    GeneratedDerivedBookAttribute themes = new GeneratedDerivedBookAttribute(
        BookDiscoveryField.THEMES,
        JsonMapper.shared().valueToTree(List.of("software craftsmanship")),
        List.of(new EvidenceReference(
            EvidenceSource.GOOGLE_BOOKS, "volume-1", "description")));
    GeneratedBookEnrichment generated = new GeneratedBookEnrichment(
        List.of(), List.of(), List.of(themes), List.of());

    ValidatedBookEnrichment result = validator.validate(request(Map.of()), generated, ledger);

    assertThat(result.derivedAttributes()).singleElement().satisfies(attribute -> {
      assertThat(attribute.field()).isEqualTo(BookDiscoveryField.THEMES);
      assertThat(attribute.derivation()).isEqualTo(DerivationType.INFERRED_FROM_EVIDENCE);
      assertThat(attribute.evidence().getFirst().sourceField()).isEqualTo("description");
    });
  }

  @Test
  void quarantinesAnInvalidDerivedValueAndKeepsAnIndependentDerivedAttribute() {
    BookCatalogEvidenceLedger ledger = exactLedger(catalogRecord("Prentice Hall", "9780132350884"));
    GeneratedDerivedBookAttribute invalidAudience = new GeneratedDerivedBookAttribute(
        BookDiscoveryField.TARGET_AUDIENCE,
        text("PROFESSIONALS"),
        List.of(new EvidenceReference(
            EvidenceSource.GOOGLE_BOOKS, "volume-1", "description")));
    GeneratedDerivedBookAttribute validLength = new GeneratedDerivedBookAttribute(
        BookDiscoveryField.BOOK_LENGTH_ESTIMATE,
        text("LONG"),
        List.of(new EvidenceReference(
            EvidenceSource.GOOGLE_BOOKS, "volume-1", "pageCount")));
    GeneratedBookEnrichment generated = new GeneratedBookEnrichment(
        List.of(), List.of(), List.of(invalidAudience, validLength), List.of());

    ValidatedBookEnrichment result = validator.validate(request(Map.of()), generated, ledger);

    assertThat(result.derivedAttributes()).singleElement().satisfies(attribute ->
        assertThat(attribute.field()).isEqualTo(BookDiscoveryField.BOOK_LENGTH_ESTIMATE));
    assertThat(ledger.warnings().toString()).contains("UNVERIFIABLE_MODEL_ITEM_REJECTED");
  }

  @Test
  void rejectsSummaryWithoutDescriptionEvidence() {
    BookCatalogEvidenceLedger ledger = exactLedger(catalogRecord("Prentice Hall", "9780132350884"));
    GeneratedDerivedBookAttribute summary = new GeneratedDerivedBookAttribute(
        BookDiscoveryField.SHORT_SUMMARY,
        text("A concise software craftsmanship handbook."),
        List.of(new EvidenceReference(EvidenceSource.GOOGLE_BOOKS, "volume-1", "title")));
    assertInvalid(() -> validator.validate(request(Map.of()),
        new GeneratedBookEnrichment(List.of(), List.of(), List.of(summary), List.of()), ledger));
  }

  @Test
  void quarantinesAWeakUnrelatedCitationForSemanticDiscoveryMetadata() {
    BookCatalogEvidenceLedger ledger = exactLedger(catalogRecord("Prentice Hall", "9780132350884"));
    GeneratedDerivedBookAttribute themes = new GeneratedDerivedBookAttribute(
        BookDiscoveryField.THEMES,
        JsonMapper.shared().valueToTree(List.of("software craftsmanship")),
        List.of(new EvidenceReference(
            EvidenceSource.GOOGLE_BOOKS, "volume-1", "publisher")));

    ValidatedBookEnrichment result = validator.validate(
        request(Map.of()),
        new GeneratedBookEnrichment(List.of(), List.of(), List.of(themes), List.of()),
        ledger);

    assertThat(result.derivedAttributes()).isEmpty();
    assertThat(ledger.warnings().toString()).contains("UNVERIFIABLE_MODEL_ITEM_REJECTED");
  }

  @Test
  void rejectsAGeneratedValueWhosePopulatedSlotDoesNotMatchItsKind() {
    BookCatalogEvidenceLedger ledger = exactLedger(catalogRecord("Prentice Hall", "9780132350884"));
    GeneratedFieldProposal proposal = new GeneratedFieldProposal(
        BookField.PUBLISHER,
        new GeneratedBookValue(
            GeneratedBookValueKind.TEXT,
            "Prentice Hall",
            List.of("Prentice Hall"),
            0),
        List.of(new EvidenceReference(
            EvidenceSource.GOOGLE_BOOKS, "volume-1", "publisher")));

    assertInvalid(() -> validator.validate(request(Map.of()),
        new GeneratedBookEnrichment(List.of(proposal), List.of(), List.of(), List.of()), ledger));
  }

  @Test
  void rejectsAnInvalidDerivedAgeRange() {
    BookCatalogEvidenceLedger ledger = exactLedger(catalogRecord("Prentice Hall", "9780132350884"));
    EvidenceReference description = new EvidenceReference(
        EvidenceSource.GOOGLE_BOOKS, "volume-1", "description");
    GeneratedDerivedBookAttribute minimum = new GeneratedDerivedBookAttribute(
        BookDiscoveryField.MIN_AGE, JsonMapper.shared().valueToTree(16), List.of(description));
    GeneratedDerivedBookAttribute maximum = new GeneratedDerivedBookAttribute(
        BookDiscoveryField.MAX_AGE, JsonMapper.shared().valueToTree(12), List.of(description));

    assertInvalid(() -> validator.validate(request(Map.of()),
        new GeneratedBookEnrichment(
            List.of(), List.of(), List.of(minimum, maximum), List.of()), ledger));
  }

  private BookEnrichmentRequest request(Map<String, String> attributes) {
    return new BookEnrichmentRequest(
        "book-123", "Clean Code", "ISBN 9780132350884", null, attributes);
  }

  private BookCatalogEvidenceLedger exactLedger(BookCatalogRecord catalogRecord) {
    BookCatalogEvidenceLedger ledger = new BookCatalogEvidenceLedger();
    ledger.recordLookup("isbn:9780132350884", CatalogLookupResult.found(
        MatchType.EXACT_ISBN, List.of(catalogRecord)));
    return ledger;
  }

  private BookCatalogRecord catalogRecord(String publisher, String isbn13) {
    return new BookCatalogRecord(
        "volume-1",
        URI.create("https://books.google.com/books?id=volume-1"),
        "Clean Code",
        null,
        List.of("Robert C. Martin"),
        publisher,
        "2008-08-01",
        "A handbook about writing maintainable software.",
        "0132350882",
        isbn13,
        "en",
        464,
        List.of("Computers"));
  }

  private GeneratedBookEnrichment generated(GeneratedFieldProposal proposal) {
    return new GeneratedBookEnrichment(List.of(proposal), List.of(), List.of(), List.of());
  }

  private GeneratedFieldProposal proposal(
      BookField field, JsonNode value, EvidenceReference evidence) {
    return new GeneratedFieldProposal(field, value, List.of(evidence));
  }

  private JsonNode text(String value) {
    return StringNode.valueOf(value);
  }

  private void assertInvalid(Runnable operation) {
    assertThatThrownBy(operation::run)
        .isInstanceOf(InvalidBookEnrichmentResponseException.class)
        .hasMessage("AI book enrichment response is invalid");
  }
}
