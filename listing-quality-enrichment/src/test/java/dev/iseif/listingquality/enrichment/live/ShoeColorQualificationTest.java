package dev.iseif.listingquality.enrichment.live;

import dev.iseif.listingquality.enrichment.ListingQualityEnrichmentApplication;
import dev.iseif.listingquality.enrichment.prompt.ShoeColorPrompt;
import dev.iseif.listingquality.enrichment.service.shoe.ShoeColorExtractionValidator;
import dev.iseif.listingquality.enrichment.service.shoe.ShoeColorGeneratorRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "RUN_SHOE_COLOR_EVALUATION", matches = "true")
@SpringBootTest(classes = ListingQualityEnrichmentApplication.class)
class ShoeColorQualificationTest {

  @Autowired
  private ShoeColorGeneratorRegistry generators;

  @Autowired
  private ShoeColorExtractionValidator validator;

  @Autowired
  private ShoeColorPrompt prompt;

  @Autowired
  private ObjectMapper objectMapper;

  @Test
  void qualifiesTheSelectedProvider() {
    String provider = requiredEnvironment("SHOE_COLOR_EVALUATION_PROVIDER");
    String model = "gemini".equals(provider)
        ? requiredEnvironment("GEMINI_CHAT_MODEL")
        : requiredEnvironment("OMLX_CHAT_MODEL");
    var dataset = ShoeColorQualificationDataset.load(
        objectMapper,
        new ClassPathResource("shoe-color-evaluation/manifest.json"));
    ShoeColorQualificationReport report = new ShoeColorQualificationRunner(prompt)
        .run(generators.require(provider), validator, dataset, 3);
    var reportPath = new ShoeColorQualificationReportWriter(objectMapper)
        .write(provider, model, report);

    assertThat(report.passesReleaseGate())
        .as("qualification report %s", reportPath.toAbsolutePath())
        .isTrue();
  }

  private String requiredEnvironment(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(name + " must be set for live qualification");
    }
    return value;
  }
}
