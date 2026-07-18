package dev.iseif.listingquality.enrichment.catalog.book.google;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

@Validated
@ConfigurationProperties("listing-quality.enrichment.catalog.google-books")
public record GoogleBooksProperties(
    @NotNull URI baseUrl,
    @NotBlank String apiKey,
    @NotNull Duration timeout,
    @Min(1) @Max(3) int maxResults,
    @Min(1024) int maxResponseBytes,
    @Min(1) @Max(5) int maxAttempts,
    @NotNull Duration initialBackoff,
    @NotNull Duration maxBackoff) {

  public GoogleBooksProperties {
    if (initialBackoff != null && (initialBackoff.isZero() || initialBackoff.isNegative())) {
      throw new IllegalArgumentException("initialBackoff must be positive");
    }
    if (maxBackoff != null && initialBackoff != null && maxBackoff.compareTo(initialBackoff) < 0) {
      throw new IllegalArgumentException("maxBackoff must not be shorter than initialBackoff");
    }
  }
}
