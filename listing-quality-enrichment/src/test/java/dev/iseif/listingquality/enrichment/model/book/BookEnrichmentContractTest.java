package dev.iseif.listingquality.enrichment.model.book;

import dev.iseif.listingquality.enrichment.ListingQualityEnrichmentApplication;
import dev.iseif.listingquality.enrichment.catalog.book.BookCatalogClient;
import dev.iseif.listingquality.enrichment.model.ExecutionMetadata;
import dev.iseif.listingquality.enrichment.model.ExecutionRoute;
import dev.iseif.listingquality.enrichment.service.book.FailoverBookEnrichmentGenerator;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.StringNode;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    classes = ListingQualityEnrichmentApplication.class,
    properties = "spring.ai.model.chat=none")
class BookEnrichmentContractTest {

  @MockitoBean
  private BookCatalogClient catalogClient;

  @MockitoBean
  private FailoverBookEnrichmentGenerator failover;

  private static final String REQUEST_JSON = """
      {
        "listingId": "book-123",
        "title": "Clean Code by Robert C Martin",
        "description": "Paperback in good condition. ISBN 9780132350884.",
        "price": 24.99,
        "attributes": { "condition": "used" }
      }
      """;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private Validator validator;

  @Test
  void deserializesTheBookRequestAndKeepsAttributesImmutable() {
    BookEnrichmentRequest request = objectMapper.readValue(REQUEST_JSON, BookEnrichmentRequest.class);

    assertThat(request.listingId()).isEqualTo("book-123");
    assertThat(request.price()).isEqualByComparingTo("24.99");
    assertThat(request.attributes()).containsEntry("condition", "used");
    assertThat(validator.validate(request)).isEmpty();
  }

  @Test
  void rejectsBlankAndOversizedSellerInput() {
    BookEnrichmentRequest blankId = objectMapper.readValue(
        REQUEST_JSON.replace("book-123", ""), BookEnrichmentRequest.class);
    BookEnrichmentRequest longDescription = objectMapper.readValue(
        REQUEST_JSON.replace(
            "Paperback in good condition. ISBN 9780132350884.", "a".repeat(5001)),
        BookEnrichmentRequest.class);

    assertThat(validator.validate(blankId)).isNotEmpty();
    assertThat(validator.validate(longDescription)).isNotEmpty();
  }

  @Test
  void rejectsMoreThanFiftyAttributes() {
    Map<String, String> attributes = new LinkedHashMap<>();
    for (int index = 0; index < 51; index++) {
      attributes.put("attribute-" + index, "value");
    }
    BookEnrichmentRequest request = new BookEnrichmentRequest(
        "book-123", "Clean Code", "Used paperback", null, attributes);

    assertThat(validator.validate(request)).isNotEmpty();
  }

  @Test
  void serializesTheProposalAndCategoryNeutralExecutionRoute() {
    Evidence evidence = new Evidence(
        EvidenceSource.GOOGLE_BOOKS,
        "google-volume-1",
        "publisher",
        URI.create("https://books.google.com/books?id=google-volume-1"),
        MatchType.EXACT_ISBN);
    FieldProposal proposal = new FieldProposal(
        BookField.PUBLISHER,
        null,
        StringNode.valueOf("Prentice Hall"),
        List.of(evidence));
    BookEnrichmentResponse response = new BookEnrichmentResponse(
        EnrichmentStatus.PROPOSALS_AVAILABLE,
        List.of(proposal),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        true,
        new ExecutionMetadata(ExecutionRoute.PRIMARY));

    var json = objectMapper.valueToTree(response);

    assertThat(json.get("status").asString()).isEqualTo("PROPOSALS_AVAILABLE");
    assertThat(json.at("/proposals/0/field").asString()).isEqualTo("PUBLISHER");
    assertThat(json.at("/proposals/0/proposedValue").asString()).isEqualTo("Prentice Hall");
    assertThat(json.at("/execution/route").asString()).isEqualTo("PRIMARY");
    assertThat(json.get("requiresSellerApproval").booleanValue()).isTrue();
  }

  @Test
  void serializesDerivedDiscoveryMetadataSeparatelyFromFactualProposals() {
    DerivedBookAttribute attribute = new DerivedBookAttribute(
        BookDiscoveryField.THEMES,
        objectMapper.valueToTree(List.of("software craftsmanship")),
        DerivationType.INFERRED_FROM_EVIDENCE,
        List.of(new Evidence(
            EvidenceSource.GOOGLE_BOOKS,
            "google-volume-1",
            "description",
            URI.create("https://books.google.com/books?id=google-volume-1"),
            MatchType.EXACT_ISBN)));
    BookEnrichmentResponse response = new BookEnrichmentResponse(
        EnrichmentStatus.PROPOSALS_AVAILABLE,
        List.of(),
        List.of(),
        List.of(attribute),
        List.of(),
        List.of(),
        true,
        new ExecutionMetadata(ExecutionRoute.PRIMARY));

    var json = objectMapper.valueToTree(response);

    assertThat(json.at("/derivedAttributes/0/field").asString()).isEqualTo("THEMES");
    assertThat(json.at("/derivedAttributes/0/derivation").asString())
        .isEqualTo("INFERRED_FROM_EVIDENCE");
    assertThat(json.at("/derivedAttributes/0/evidence/0/sourceField").asString())
        .isEqualTo("description");
  }

  @Test
  void generatedSchemaUsesTypedValueSlotsInsteadOfAnUnconstrainedJsonObject() {
    String schema = new BeanOutputConverter<>(GeneratedBookEnrichment.class).getJsonSchema();
    var json = objectMapper.readTree(schema);

    var typedValueSchema = findTypedValueSchema(json);

    assertThat(typedValueSchema).isNotNull();
    assertThat(typedValueSchema.get("properties").propertyNames())
        .containsExactlyInAnyOrder("kind", "text", "items", "integer");
    assertThat(typedValueSchema.get("required")).isNotNull();
    assertThat(typedValueSchema.get("required")).extracting(JsonNode::asString)
        .containsExactlyInAnyOrder("kind", "text", "items", "integer");
  }

  private JsonNode findTypedValueSchema(JsonNode node) {
    var properties = node.get("properties");
    if (properties != null
        && properties.propertyNames().containsAll(List.of("kind", "text", "items", "integer"))) {
      return node;
    }
    for (var child : node) {
      var match = findTypedValueSchema(child);
      if (match != null) {
        return match;
      }
    }
    return null;
  }
}
