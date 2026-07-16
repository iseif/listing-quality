package dev.iseif.listingquality.evaluation.compare;

import dev.iseif.listingquality.evaluation.result.CandidateRun;
import dev.iseif.listingquality.evaluation.result.EvaluationManifest;

import java.util.List;
import java.util.Objects;

public final class RunCompatibilityValidator {

  public void validate(List<CandidateRun> runs) {
    if (runs.isEmpty()) {
      throw new IllegalArgumentException("At least one completed candidate run is required");
    }
    EvaluationManifest baseline = runs.getFirst().manifest();
    for (CandidateRun run : runs.subList(1, runs.size())) {
      EvaluationManifest candidate = run.manifest();
      same("schemaVersion", baseline.schemaVersion(), candidate.schemaVersion(), baseline, candidate);
      same("experiment", baseline.experiment(), candidate.experiment(), baseline, candidate);
      same("datasetVersion", baseline.datasetVersion(), candidate.datasetVersion(), baseline, candidate);
      same("datasetChecksum", baseline.datasetChecksum(), candidate.datasetChecksum(), baseline, candidate);
      same("rubricVersion", baseline.rubricVersion(), candidate.rubricVersion(), baseline, candidate);
      same("sourceRevision", baseline.sourceRevision(), candidate.sourceRevision(), baseline, candidate);
      same("tokenLimit", baseline.tokenLimit(), candidate.tokenLimit(), baseline, candidate);
      same("repetitions", baseline.repetitions(), candidate.repetitions(), baseline, candidate);
    }
  }

  private void same(
      String field, Object baselineValue, Object candidateValue,
      EvaluationManifest baseline, EvaluationManifest candidate) {
    if (!Objects.equals(baselineValue, candidateValue)) {
      mismatch(field, baseline, candidate);
    }
  }

  private void mismatch(
      String field, EvaluationManifest baseline, EvaluationManifest candidate) {
    throw new IllegalArgumentException(
        "Incompatible %s between %s and %s".formatted(
            field, baseline.candidate(), candidate.candidate()));
  }
}
