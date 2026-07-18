package dev.iseif.listingquality.enrichment.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

@Validated
@ConfigurationProperties("listing-quality.enrichment")
public record EnrichmentProperties(
    @NotBlank String primary,
    @NotBlank String fallback,
    @NotEmpty Map<@NotBlank String, @Valid Provider> providers,
    @NotNull @Valid Resilience resilience) {

  private static final Set<String> SUPPORTED_PROVIDERS = Set.of("gemini", "omlx");

  public EnrichmentProperties {
    providers = providers == null ? Map.of() : Map.copyOf(providers);
  }

  public Provider provider(String providerId) {
    Provider provider = providers.get(providerId);
    if (provider == null) {
      throw new IllegalArgumentException("Unknown enrichment provider");
    }
    return provider;
  }

  @AssertTrue(message = "primary and fallback must be different supported providers")
  public boolean isRoutingValid() {
    return primary != null
        && fallback != null
        && !primary.equals(fallback)
        && SUPPORTED_PROVIDERS.contains(primary)
        && SUPPORTED_PROVIDERS.contains(fallback)
        && providers.keySet().containsAll(Set.of(primary, fallback));
  }

  @AssertTrue(message = "the oMLX provider requires a base URL")
  public boolean isOmlxEndpointConfigured() {
    Provider omlx = providers.get("omlx");
    return omlx == null || omlx.baseUrl() != null;
  }

  public record Provider(
      @NotBlank String model,
      URI baseUrl,
      @NotBlank String apiKey) {
  }

  public record Resilience(
      @Min(1) @Max(3) int maxAttempts,
      @NotNull Duration retryWait,
      @Min(1) @Max(3) int structuredOutputAttempts,
      @NotNull Duration primaryTimeout,
      @NotNull Duration fallbackTimeout,
      @NotNull Duration overallTimeout,
      @Min(2) int circuitWindow,
      @DecimalMin("1.0") @DecimalMax("100.0") float circuitFailureThreshold,
      @NotNull Duration circuitOpenDuration,
      @Min(1) int halfOpenCalls) {

    @AssertTrue(message = "all resilience durations must be positive")
    public boolean areDurationsPositive() {
      return isPositive(retryWait)
          && isPositive(primaryTimeout)
          && isPositive(fallbackTimeout)
          && isPositive(overallTimeout)
          && isPositive(circuitOpenDuration);
    }

    private static boolean isPositive(Duration duration) {
      return duration != null && !duration.isZero() && !duration.isNegative();
    }
  }
}
