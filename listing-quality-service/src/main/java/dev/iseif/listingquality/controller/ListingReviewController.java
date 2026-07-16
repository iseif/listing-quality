package dev.iseif.listingquality.controller;

import dev.iseif.listingquality.model.ListingDraft;
import dev.iseif.listingquality.model.ListingReview;
import dev.iseif.listingquality.service.ListingReviewService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/listings")
public class ListingReviewController {

  private final ListingReviewService listingReviewService;

  public ListingReviewController(ListingReviewService listingReviewService) {
    this.listingReviewService = listingReviewService;
  }

  @PostMapping(path = "/review", version = "1")
  public ListingReview review(@Valid @RequestBody ListingDraft listingDraft) {
    return listingReviewService.review(listingDraft);
  }
}
