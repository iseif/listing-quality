package dev.iseif.listingquality.enrichment.model;

import jakarta.validation.constraints.NotNull;

public record ExecutionMetadata(@NotNull ExecutionRoute route) {
}
