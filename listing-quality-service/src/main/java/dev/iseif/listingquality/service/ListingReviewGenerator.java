package dev.iseif.listingquality.service;

import dev.iseif.listingquality.model.ListingReview;

public interface ListingReviewGenerator {
  ListingReview generate(String prompt);
}
