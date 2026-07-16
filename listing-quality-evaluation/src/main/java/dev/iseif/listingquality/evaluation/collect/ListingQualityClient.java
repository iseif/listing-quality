package dev.iseif.listingquality.evaluation.collect;

import dev.iseif.listingquality.evaluation.api.ListingDraftPayload;

import java.time.Duration;

public interface ListingQualityClient {
  void awaitReady(int attempts, Duration delay);

  ReviewCallResult review(ListingDraftPayload listing);
}
