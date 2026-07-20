package dev.iseif.listingquality.enrichment.media;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Validated
@ConfigurationProperties("listing-quality.enrichment.images")
public record ImageLoadingProperties(
    @NotNull Set<String> allowedHosts,
    @Min(1) @Max(3) int maxImages,
    @Min(1) long maxImageBytes,
    @Min(1) long maxTotalBytes,
    @Min(1) long maxPixels,
    @NotNull Duration connectTimeout,
    @NotNull Duration requestTimeout,
    @Min(1) int maxAttempts,
    @NotNull Duration retryWait) {

  public ImageLoadingProperties {
    allowedHosts = allowedHosts == null
        ? Set.of()
        : allowedHosts.stream()
            .map(host -> host.toLowerCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());
  }
}
