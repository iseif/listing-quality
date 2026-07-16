package dev.iseif.listingquality.evaluation.compare;

import dev.iseif.listingquality.evaluation.dataset.EvaluationDataset;
import dev.iseif.listingquality.evaluation.result.CandidateRun;
import dev.iseif.listingquality.evaluation.result.SampleResult;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class SemanticAssessmentSource {

  private static final int SUPPORTED_SOURCE_SCHEMA_VERSION = 1;

  private final ObjectMapper objectMapper;

  public SemanticAssessmentSource(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public List<SampleAssessment> load(
      Path reportPath,
      String experiment,
      EvaluationDataset dataset,
      List<CandidateRun> runs) {
    SemanticSourceReport report = read(reportPath);
    validateIdentity(report, experiment, dataset);

    List<AssessmentKey> expectedOrder = expectedKeys(runs);
    Set<AssessmentKey> expected = new LinkedHashSet<>(expectedOrder);
    Map<AssessmentKey, SampleAssessment> actual = new LinkedHashMap<>();
    for (SampleAssessment assessment : report.assessments()) {
      if (assessment.semanticStatus() != SemanticStatus.EVALUATED
          || assessment.expectedConceptCoverage() == null) {
        throw new IllegalArgumentException(
            "Semantic-source assessments must be evaluated with concept coverage");
      }
      AssessmentKey key = key(assessment);
      if (actual.putIfAbsent(key, assessment) != null) {
        throw new IllegalArgumentException("Semantic source contains duplicate assessment key");
      }
    }

    Set<AssessmentKey> unknown = new LinkedHashSet<>(actual.keySet());
    unknown.removeAll(expected);
    if (!unknown.isEmpty()) {
      throw new IllegalArgumentException("Semantic source contains unknown assessment keys");
    }
    Set<AssessmentKey> missing = new LinkedHashSet<>(expected);
    missing.removeAll(actual.keySet());
    if (!missing.isEmpty()) {
      throw new IllegalArgumentException("Semantic source is missing assessment keys");
    }

    return expectedOrder.stream().map(actual::get).toList();
  }

  private SemanticSourceReport read(Path reportPath) {
    if (!Files.isRegularFile(reportPath)) {
      throw new IllegalArgumentException(
          "Semantic-source report does not exist: " + reportPath);
    }
    try {
      return objectMapper.readValue(Files.readAllBytes(reportPath), SemanticSourceReport.class);
    } catch (IOException | RuntimeException exception) {
      throw new IllegalArgumentException("Semantic-source report could not be read");
    }
  }

  private void validateIdentity(
      SemanticSourceReport report,
      String experiment,
      EvaluationDataset dataset) {
    if (report.schemaVersion() != SUPPORTED_SOURCE_SCHEMA_VERSION) {
      throw new IllegalArgumentException(
          "Unsupported semantic-source schema version: " + report.schemaVersion());
    }
    if (!experiment.equals(report.experiment())) {
      throw new IllegalArgumentException("Semantic source belongs to another experiment");
    }
    if (!dataset.manifest().datasetVersion().equals(report.datasetVersion())
        || !dataset.checksum().equals(report.datasetChecksum())) {
      throw new IllegalArgumentException("Semantic source belongs to another dataset");
    }
    if (!dataset.manifest().rubricVersion().equals(report.rubricVersion())) {
      throw new IllegalArgumentException("Semantic source uses another semantic rubric");
    }
  }

  private List<AssessmentKey> expectedKeys(List<CandidateRun> runs) {
    List<AssessmentKey> keys = new ArrayList<>();
    for (CandidateRun run : runs) {
      for (SampleResult sample : run.samples()) {
        keys.add(new AssessmentKey(
            run.manifest().candidate(), sample.caseId(), sample.repetition()));
      }
    }
    return keys;
  }

  private AssessmentKey key(SampleAssessment assessment) {
    return new AssessmentKey(
        assessment.candidate(), assessment.caseId(), assessment.repetition());
  }

  private record AssessmentKey(String candidate, String caseId, int repetition) {}
}
