package dev.iseif.listingquality.evaluation.grade;

import dev.iseif.listingquality.evaluation.dataset.ScoreRange;
import dev.iseif.listingquality.evaluation.result.SampleResult;
import dev.iseif.listingquality.evaluation.result.SampleStatus;

public final class ContractGrader {

  public boolean isValid(SampleResult sample) {
    return sample.status() == SampleStatus.SUCCEEDED && sample.review() != null;
  }

  public boolean scoreInRange(SampleResult sample, ScoreRange range) {
    return isValid(sample)
        && sample.review().qualityScore() >= range.minimum()
        && sample.review().qualityScore() <= range.maximum();
  }
}
