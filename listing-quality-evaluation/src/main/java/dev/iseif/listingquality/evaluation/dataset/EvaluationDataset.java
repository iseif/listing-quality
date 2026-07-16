package dev.iseif.listingquality.evaluation.dataset;

import java.util.List;

public record EvaluationDataset(
    DatasetManifest manifest,
    List<EvaluationCase> cases,
    String checksum) {

  public EvaluationDataset {
    cases = List.copyOf(cases);
  }
}
