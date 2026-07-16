package dev.iseif.listingquality.evaluation.collect;

import dev.iseif.listingquality.evaluation.api.ListingReviewPayload;

public record ReviewSuccess(ListingReviewPayload review, long durationMs)
    implements ReviewCallResult {}
