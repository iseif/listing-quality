package dev.iseif.listingquality.service;

import dev.iseif.listingquality.model.ListingDraft;
import dev.iseif.listingquality.model.ListingReview;
import dev.iseif.listingquality.observability.ListingReviewTelemetry;
import dev.iseif.listingquality.prompt.ListingReviewPrompt;
import org.springframework.stereotype.Service;

@Service
public class ListingReviewService {
  private final ListingReviewPrompt listingReviewPrompt;
  private final ListingReviewGenerator listingReviewGenerator;
  private final ListingReviewValidator listingReviewValidator;
  private final ListingReviewTelemetry telemetry;

  public ListingReviewService(
      ListingReviewPrompt listingReviewPrompt,
      ListingReviewGenerator listingReviewGenerator,
      ListingReviewValidator listingReviewValidator,
      ListingReviewTelemetry telemetry) {
    this.listingReviewPrompt = listingReviewPrompt;
    this.listingReviewGenerator = listingReviewGenerator;
    this.listingReviewValidator = listingReviewValidator;
    this.telemetry = telemetry;
  }

  public ListingReview review(ListingDraft listingDraft) {
    return telemetry.observe(() -> {
      String renderedPrompt = listingReviewPrompt.render(listingDraft);
      ListingReview listingReview = listingReviewGenerator.generate(renderedPrompt);
      return listingReviewValidator.validate(listingReview);
    });
  }
}
