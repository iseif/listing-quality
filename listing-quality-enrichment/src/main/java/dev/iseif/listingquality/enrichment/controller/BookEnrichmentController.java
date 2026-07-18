package dev.iseif.listingquality.enrichment.controller;

import dev.iseif.listingquality.enrichment.model.book.BookEnrichmentRequest;
import dev.iseif.listingquality.enrichment.model.book.BookEnrichmentResponse;
import dev.iseif.listingquality.enrichment.service.book.BookEnrichmentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/enrichments")
public class BookEnrichmentController {

  private final BookEnrichmentService service;

  public BookEnrichmentController(BookEnrichmentService service) {
    this.service = service;
  }

  @PostMapping(path = "/books", version = "1")
  public BookEnrichmentResponse enrich(@Valid @RequestBody BookEnrichmentRequest request) {
    return service.enrich(request);
  }
}
