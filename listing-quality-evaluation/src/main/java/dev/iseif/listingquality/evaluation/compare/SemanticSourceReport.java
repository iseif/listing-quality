package dev.iseif.listingquality.evaluation.compare;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SemanticSourceReport(
    int schemaVersion,
    String experiment,
    String datasetVersion,
    String datasetChecksum,
    String rubricVersion,
    List<SampleAssessment> assessments) {

  public SemanticSourceReport {
    assessments = assessments == null ? List.of() : List.copyOf(assessments);
  }
}
