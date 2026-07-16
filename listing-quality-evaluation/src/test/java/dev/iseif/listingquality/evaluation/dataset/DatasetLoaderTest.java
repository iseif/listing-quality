package dev.iseif.listingquality.evaluation.dataset;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatasetLoaderTest {

  private final DatasetLoader loader = new DatasetLoader(
      new ObjectMapper(), Validation.buildDefaultValidatorFactory().getValidator());

  @TempDir
  Path temporaryDirectory;

  @Test
  void loadsTheVersionedTwelveCaseDataset() {
    EvaluationDataset dataset = loader.load("listing-quality-v1");

    assertThat(dataset.manifest().schemaVersion()).isEqualTo(1);
    assertThat(dataset.manifest().datasetVersion()).isEqualTo("listing-quality-v1");
    assertThat(dataset.manifest().rubricVersion()).isEqualTo("listing-quality-rubric-v1");
    assertThat(dataset.cases()).hasSize(12);
    assertThat(dataset.cases()).extracting(EvaluationCase::id).doesNotHaveDuplicates();
    assertThat(dataset.checksum()).hasSize(64).matches("[0-9a-f]{64}");
  }

  @Test
  void rejectsAnUnsupportedSchemaVersion() throws IOException {
    Paths paths = copyValidDataset();
    Files.writeString(paths.manifest(), """
        {
          "schemaVersion": 2,
          "datasetVersion": "listing-quality-v1",
          "rubricVersion": "listing-quality-rubric-v1",
          "warmupCaseId": "complete-running-shoes"
        }
        """, StandardCharsets.UTF_8);

    assertThatThrownBy(() -> loader.load(paths.manifest(), paths.cases()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unsupported dataset schema version: 2");
  }

  @Test
  void rejectsDuplicateCaseIds() throws IOException {
    Paths paths = copyValidDataset();
    String first = Files.readAllLines(paths.cases()).getFirst();
    Files.writeString(paths.cases(), first + System.lineSeparator() + first,
        StandardCharsets.UTF_8);

    assertThatThrownBy(() -> loader.load(paths.manifest(), paths.cases()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Duplicate evaluation case ID: complete-running-shoes");
  }

  @Test
  void rejectsARequiredWarmupCaseThatIsAbsent() throws IOException {
    Paths paths = copyValidDataset();
    String manifest = Files.readString(paths.manifest())
        .replace("complete-running-shoes", "absent-case");
    Files.writeString(paths.manifest(), manifest, StandardCharsets.UTF_8);

    assertThatThrownBy(() -> loader.load(paths.manifest(), paths.cases()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Warm-up case does not exist: absent-case");
  }

  @Test
  void rejectsAnInvalidScoreRange() throws IOException {
    Paths paths = copyValidDataset();
    String cases = Files.readString(paths.cases())
        .replaceFirst("\\\"minimum\\\":75,\\\"maximum\\\":100",
            "\\\"minimum\\\":90,\\\"maximum\\\":80");
    Files.writeString(paths.cases(), cases, StandardCharsets.UTF_8);

    assertThatThrownBy(() -> loader.load(paths.manifest(), paths.cases()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("minimum must not exceed maximum");
  }

  @Test
  void rejectsAListingThatViolatesThePublicRequestContract() throws IOException {
    Paths paths = copyValidDataset();
    String cases = Files.readString(paths.cases())
        .replaceFirst("\\\"title\\\":\\\"Complete running shoes\\\"", "\\\"title\\\":\\\"\\\"");
    Files.writeString(paths.cases(), cases, StandardCharsets.UTF_8);

    assertThatThrownBy(() -> loader.load(paths.manifest(), paths.cases()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("listing.title");
  }

  @Test
  void rejectsAnythingOtherThanTwelveCases() throws IOException {
    Paths paths = copyValidDataset();
    String elevenCases = Files.readAllLines(paths.cases()).stream().limit(11)
        .reduce((left, right) -> left + System.lineSeparator() + right)
        .orElseThrow();
    Files.writeString(paths.cases(), elevenCases, StandardCharsets.UTF_8);

    assertThatThrownBy(() -> loader.load(paths.manifest(), paths.cases()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Expected 12 evaluation cases but found 11");
  }

  private Paths copyValidDataset() throws IOException {
    Path sourceRoot = Path.of("src/main/resources/datasets/listing-quality-v1");
    Path manifest = temporaryDirectory.resolve("manifest.json");
    Path cases = temporaryDirectory.resolve("cases.jsonl");
    Files.copy(sourceRoot.resolve("manifest.json"), manifest);
    Files.copy(sourceRoot.resolve("cases.jsonl"), cases);
    return new Paths(manifest, cases);
  }

  private record Paths(Path manifest, Path cases) {}
}
