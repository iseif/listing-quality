package dev.iseif.listingquality.enrichment.controller;

import dev.iseif.listingquality.enrichment.model.shoe.ShoeColorEnrichmentRequest;
import dev.iseif.listingquality.enrichment.model.shoe.ShoeColorEnrichmentResponse;
import dev.iseif.listingquality.enrichment.service.shoe.ShoeColorEnrichmentService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/enrichments")
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "google-genai")
public final class ShoeColorEnrichmentController {

  private final ShoeColorEnrichmentService service;

  public ShoeColorEnrichmentController(ShoeColorEnrichmentService service) {
    this.service = service;
  }

  @PostMapping(path = "/shoes/colors", version = "1")
  public ShoeColorEnrichmentResponse enrich(
      @Valid @RequestBody ShoeColorEnrichmentRequest request) {
    return service.enrich(request);
  }
}
