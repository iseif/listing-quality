package dev.iseif.listingquality.enrichment.model.shoe;

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
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
    classes = ListingQualityEnrichmentApplication.class,
    properties = "spring.ai.model.chat=none")
class ShoeColorContractTest {

  @MockitoBean
  private BookCatalogClient catalogClient;

  @MockitoBean
  private FailoverBookEnrichmentGenerator failover;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private Validator validator;

  @Test
  void deserializesListingColorsAndImageUrls() {
    ShoeColorEnrichmentRequest request = objectMapper.readValue("""
        {
          "listingId": "shoe-123",
          "listingColors": ["GREEN", "WHITE"],
          "imageUrls": ["https://cdn.example.com/front.jpg"]
        }
        """, ShoeColorEnrichmentRequest.class);

    assertThat(request.listingColors()).containsExactly(ShoeColor.GREEN, ShoeColor.WHITE);
    assertThat(request.imageUrls()).containsExactly(
        URI.create("https://cdn.example.com/front.jpg"));
    assertThat(validator.validate(request)).isEmpty();
  }

  @Test
  void defensivelyCopiesRequestCollections() {
    var listingColors = new ArrayList<>(List.of(ShoeColor.GREEN));
    var imageUrls = new ArrayList<>(List.of(URI.create("https://cdn.example.com/front.jpg")));

    ShoeColorEnrichmentRequest request = new ShoeColorEnrichmentRequest(
        "shoe-123", listingColors, imageUrls);
    listingColors.add(ShoeColor.WHITE);
    imageUrls.add(URI.create("https://cdn.example.com/side.jpg"));

    assertThat(request.listingColors()).containsExactly(ShoeColor.GREEN);
    assertThat(request.imageUrls()).containsExactly(
        URI.create("https://cdn.example.com/front.jpg"));
    assertThatThrownBy(() -> request.listingColors().add(ShoeColor.WHITE))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void validatesOneToThreeImageUrls() {
    var noImages = new ShoeColorEnrichmentRequest("shoe-123", List.of(), List.of());
    var fourImages = new ShoeColorEnrichmentRequest(
        "shoe-123",
        List.of(),
        List.of(
            URI.create("https://cdn.example.com/1.jpg"),
            URI.create("https://cdn.example.com/2.jpg"),
            URI.create("https://cdn.example.com/3.jpg"),
            URI.create("https://cdn.example.com/4.jpg")));

    assertThat(validator.validate(noImages)).isNotEmpty();
    assertThat(validator.validate(fourImages)).isNotEmpty();
  }

  @Test
  void rejectsDuplicateListingColorsAndNormalizedUrls() {
    var duplicateColors = new ShoeColorEnrichmentRequest(
        "shoe-123",
        List.of(ShoeColor.GREEN, ShoeColor.GREEN),
        List.of(URI.create("https://cdn.example.com/front.jpg")));
    var duplicateUrls = new ShoeColorEnrichmentRequest(
        "shoe-123",
        List.of(ShoeColor.GREEN),
        List.of(
            URI.create("HTTPS://CDN.EXAMPLE.COM/front.jpg"),
            URI.create("https://cdn.example.com/front.jpg")));

    assertThat(validator.validate(duplicateColors)).isNotEmpty();
    assertThat(validator.validate(duplicateUrls)).isNotEmpty();
  }

  @Test
  void generatedSchemaUsesBoundedEnumsAndLists() {
    String schema = new BeanOutputConverter<>(GeneratedShoeColorExtraction.class).getJsonSchema();

    assertThat(schema)
        .contains("COLORS_OBSERVED", "INCONCLUSIVE", "OUTSOLE_ONLY", "GREEN")
        .contains("PRIMARY", "ADDITIONAL")
        .doesNotContain("confidence", "reasoning");
  }

  @Test
  void serializesThePublicResponseWithoutModelConfidence() {
    var response = new ShoeColorEnrichmentResponse(
        ShoeColorStatus.PROPOSALS_AVAILABLE,
        List.of(new ObservedShoeColor(
            ShoeColor.GREEN, ShoeColorRole.PRIMARY, List.of("IMAGE_1"))),
        List.of(new IgnoredShoeImage("IMAGE_2", ShoeImageAssessmentReason.OUTSOLE_ONLY)),
        List.of(new ShoeColorProposal(
            ShoeField.COLORS,
            List.of(ShoeColor.GREEN, ShoeColor.WHITE),
            List.of(ShoeColor.GREEN, ShoeColor.WHITE, ShoeColor.BROWN))),
        List.of(),
        List.of(),
        true,
        new ExecutionMetadata(ExecutionRoute.PRIMARY));

    var json = objectMapper.valueToTree(response);

    assertThat(json.at("/status").asString()).isEqualTo("PROPOSALS_AVAILABLE");
    assertThat(json.at("/observedColors/0/color").asString()).isEqualTo("GREEN");
    assertThat(json.at("/ignoredImages/0/reason").asString()).isEqualTo("OUTSOLE_ONLY");
    assertThat(json.at("/proposals/0/field").asString()).isEqualTo("COLORS");
    assertThat(json.at("/execution/route").asString()).isEqualTo("PRIMARY");
    assertThat(json.toString()).doesNotContain("confidence", "reasoning");
  }
}
