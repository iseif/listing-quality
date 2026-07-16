package dev.iseif.listingquality.evaluation.compare;

import java.util.List;

public final class Percentiles {

  private Percentiles() {}

  public static long median(List<Long> values) {
    List<Long> sorted = values.stream().sorted().toList();
    if (sorted.isEmpty()) {
      return 0;
    }
    int middle = sorted.size() / 2;
    if (sorted.size() % 2 == 1) {
      return sorted.get(middle);
    }
    return Math.round((sorted.get(middle - 1) + sorted.get(middle)) / 2.0);
  }

  public static long nearestRank(List<Long> values, double percentile) {
    List<Long> sorted = values.stream().sorted().toList();
    if (sorted.isEmpty()) {
      return 0;
    }
    int rank = Math.max(1, (int) Math.ceil(percentile * sorted.size()));
    return sorted.get(rank - 1);
  }
}
