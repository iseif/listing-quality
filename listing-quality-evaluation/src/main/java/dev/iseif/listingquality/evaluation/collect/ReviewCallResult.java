package dev.iseif.listingquality.evaluation.collect;

public sealed interface ReviewCallResult permits ReviewSuccess, ReviewFailure {
  long durationMs();
}
