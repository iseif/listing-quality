package dev.iseif.listingquality.evaluation.compare;

import java.util.List;

public record ScorecardReport(
    int schemaVersion,
    String experiment,
    String datasetVersion,
    String datasetChecksum,
    String semanticRubricVersion,
    String routingPolicyVersion,
    String routingPolicyChecksum,
    List<CandidateScorecard> candidates,
    List<SampleAssessment> assessments,
    List<String> limitations) {

  public ScorecardReport {
    candidates = List.copyOf(candidates);
    assessments = List.copyOf(assessments);
    limitations = List.copyOf(limitations);
  }
}
