package dev.iseif.listingquality.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "listing-quality.ai")
public record ListingReviewAiProperties(
    @DefaultValue("0.2") Double temperature) {
}
