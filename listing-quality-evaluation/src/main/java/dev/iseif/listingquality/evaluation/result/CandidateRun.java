package dev.iseif.listingquality.evaluation.result;

import java.util.List;

public record CandidateRun(EvaluationManifest manifest, List<SampleResult> samples) {

  public CandidateRun {
    samples = List.copyOf(samples);
  }
}
