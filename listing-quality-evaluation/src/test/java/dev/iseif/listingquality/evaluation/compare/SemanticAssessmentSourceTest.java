package dev.iseif.listingquality.evaluation.compare;

import dev.iseif.listingquality.evaluation.api.ListingReviewPayload;
import dev.iseif.listingquality.evaluation.dataset.DatasetLoader;
import dev.iseif.listingquality.evaluation.dataset.EvaluationDataset;
import dev.iseif.listingquality.evaluation.grade.HumanReviewOutcome;
import dev.iseif.listingquality.evaluation.result.*;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticAssessmentSourceTest {

  @TempDir
  Path temporaryDirectory;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final EvaluationDataset dataset = new DatasetLoader(
      objectMapper, Validation.buildDefaultValidatorFactory().getValidator())
      .load("listing-quality-v1");
  private final SemanticAssessmentSource source = new SemanticAssessmentSource(objectMapper);
  private final CandidateRun run = run();

  @Test
  void loadsACompleteCompatibleSemanticSource() throws Exception {
    Path report = writeReport(
        "listing-quality-v1", dataset.checksum(), dataset.manifest().rubricVersion(),
        List.of(assessment("candidate-1", SemanticStatus.EVALUATED, 85)));

    List<SampleAssessment> loaded = source.load(
        report, "listing-quality-v1", dataset, List.of(run));

    assertThat(loaded).hasSize(1);
    assertThat(loaded.getFirst().expectedConceptCoverage()).isEqualTo(85);
  }

  @Test
  void rejectsAnotherExperimentDatasetOrRubric() throws Exception {
    Path anotherExperiment = writeReport(
        "another-experiment", dataset.checksum(), dataset.manifest().rubricVersion(),
        List.of(assessment("candidate-1", SemanticStatus.EVALUATED, 85)));
    assertThatThrownBy(() -> source.load(
        anotherExperiment, "listing-quality-v1", dataset, List.of(run)))
        .hasMessageContaining("another experiment");

    Path anotherDataset = writeReport(
        "listing-quality-v1", "b".repeat(64), dataset.manifest().rubricVersion(),
        List.of(assessment("candidate-1", SemanticStatus.EVALUATED, 85)));
    assertThatThrownBy(() -> source.load(
        anotherDataset, "listing-quality-v1", dataset, List.of(run)))
        .hasMessageContaining("another dataset");

    Path anotherRubric = writeReport(
        "listing-quality-v1", dataset.checksum(), "another-rubric",
        List.of(assessment("candidate-1", SemanticStatus.EVALUATED, 85)));
    assertThatThrownBy(() -> source.load(
        anotherRubric, "listing-quality-v1", dataset, List.of(run)))
        .hasMessageContaining("another semantic rubric");
  }

  @Test
  void rejectsDuplicateMissingAndUnknownAssessmentKeys() throws Exception {
    SampleAssessment valid = assessment("candidate-1", SemanticStatus.EVALUATED, 85);
    Path duplicate = writeReport(
        "listing-quality-v1", dataset.checksum(), dataset.manifest().rubricVersion(),
        List.of(valid, valid));
    assertThatThrownBy(() -> source.load(
        duplicate, "listing-quality-v1", dataset, List.of(run)))
        .hasMessageContaining("duplicate assessment key");

    Path missing = writeReport(
        "listing-quality-v1", dataset.checksum(), dataset.manifest().rubricVersion(),
        List.of());
    assertThatThrownBy(() -> source.load(
        missing, "listing-quality-v1", dataset, List.of(run)))
        .hasMessageContaining("missing assessment keys");

    Path unknown = writeReport(
        "listing-quality-v1", dataset.checksum(), dataset.manifest().rubricVersion(),
        List.of(assessment("unknown-candidate", SemanticStatus.EVALUATED, 85)));
    assertThatThrownBy(() -> source.load(
        unknown, "listing-quality-v1", dataset, List.of(run)))
        .hasMessageContaining("unknown assessment keys");
  }

  @Test
  void rejectsUnevaluatedSemanticRows() throws Exception {
    Path report = writeReport(
        "listing-quality-v1", dataset.checksum(), dataset.manifest().rubricVersion(),
        List.of(assessment("candidate-1", SemanticStatus.NOT_EVALUATED, null)));

    assertThatThrownBy(() -> source.load(
        report, "listing-quality-v1", dataset, List.of(run)))
        .hasMessageContaining("must be evaluated");
  }

  private Path writeReport(
      String experiment,
      String checksum,
      String rubric,
      List<SampleAssessment> assessments) throws Exception {
    Path path = temporaryDirectory.resolve("comparison-" + System.nanoTime() + ".json");
    byte[] json = objectMapper.writeValueAsBytes(Map.of(
        "schemaVersion", 1,
        "experiment", experiment,
        "datasetVersion", dataset.manifest().datasetVersion(),
        "datasetChecksum", checksum,
        "rubricVersion", rubric,
        "candidates", List.of(Map.of("ignored", true)),
        "assessments", assessments,
        "limitations", List.of("ignored")));
    Files.write(path, json);
    return path;
  }

  private SampleAssessment assessment(
      String candidate, SemanticStatus status, Integer coverage) {
    return new SampleAssessment(
        candidate,
        "complete-running-shoes",
        1,
        true,
        true,
        HumanReviewOutcome.TRUE_NEGATIVE,
        List.of(),
        status,
        coverage,
        status == SemanticStatus.EVALUATED ? "covered" : null);
  }

  private CandidateRun run() {
    SampleResult sample = new SampleResult(
        "complete-running-shoes",
        1,
        SampleStatus.SUCCEEDED,
        new ListingReviewPayload(90, List.of(), List.of(), List.of(), false),
        null,
        10);
    EvaluationManifest manifest = new EvaluationManifest(
        1,
        "listing-quality-v1",
        "candidate-1",
        "test-provider",
        "test-model",
        ModelIdentitySource.RUNTIME_METRICS,
        dataset.manifest().datasetVersion(),
        dataset.checksum(),
        dataset.manifest().rubricVersion(),
        new SourceRevision("abc", true, "d".repeat(64)),
        new RuntimeEnvironment(
            "test-runtime", "1", "test-os", "test-arch", "test-cpu", 1024, "25"),
        new BigDecimal("0.2"),
        800,
        1,
        Instant.parse("2026-07-15T10:00:00Z"),
        Instant.parse("2026-07-15T10:01:00Z"),
        10,
        TokenUsage.notReported(),
        TokenUsage.notReported());
    return new CandidateRun(manifest, List.of(sample));
  }
}
