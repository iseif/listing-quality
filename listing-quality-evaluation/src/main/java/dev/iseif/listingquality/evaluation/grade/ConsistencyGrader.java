package dev.iseif.listingquality.evaluation.grade;

import dev.iseif.listingquality.evaluation.result.SampleResult;
import dev.iseif.listingquality.evaluation.result.SampleStatus;

import java.util.List;

public final class ConsistencyGrader {

  public boolean isSeverelyInconsistent(List<SampleResult> repetitions) {
    long successCount = repetitions.stream()
        .filter(sample -> sample.status() == SampleStatus.SUCCEEDED)
        .count();
    if (successCount > 0 && successCount < repetitions.size()) {
      return true;
    }
    if (successCount == 0) {
      return false;
    }
    long humanReviewValues = repetitions.stream()
        .map(sample -> sample.review().requiresHumanReview())
        .distinct()
        .count();
    if (humanReviewValues > 1) {
      return true;
    }
    int minimum = repetitions.stream().mapToInt(sample -> sample.review().qualityScore()).min()
        .orElseThrow();
    int maximum = repetitions.stream().mapToInt(sample -> sample.review().qualityScore()).max()
        .orElseThrow();
    return maximum - minimum > 25;
  }
}
