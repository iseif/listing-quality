package dev.iseif.listingquality.enrichment.service.shoe;

import dev.iseif.listingquality.enrichment.model.ExecutionRoute;

public record ShoeColorExecution(
    ValidatedShoeColorExtraction extraction,
    ExecutionRoute route) {
}
