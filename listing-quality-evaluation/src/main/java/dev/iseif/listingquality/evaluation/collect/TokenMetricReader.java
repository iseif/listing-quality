package dev.iseif.listingquality.evaluation.collect;

import dev.iseif.listingquality.evaluation.result.TokenUsage;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.util.List;

public final class TokenMetricReader implements TokenUsageReader {

  private static final String METRIC_PATH = "/actuator/metrics/gen_ai.client.token.usage";

  private final RestClient restClient;

  public TokenMetricReader(RestClient.Builder builder, URI baseUrl) {
    this.restClient = builder.baseUrl(baseUrl.toString()).build();
  }

  @Override
  public TokenMetricSnapshot read() {
    MetricResponse base;
    try {
      base = restClient.get().uri(METRIC_PATH).retrieve().body(MetricResponse.class);
    } catch (RestClientException exception) {
      return new TokenMetricSnapshot(TokenUsage.notReported(), null);
    }
    if (base == null) {
      return new TokenMetricSnapshot(TokenUsage.notReported(), null);
    }

    String responseModel = base.availableTags() == null ? null : base.availableTags().stream()
        .filter(tag -> "gen_ai.response.model".equals(tag.tag()))
        .flatMap(tag -> tag.values().stream())
        .findFirst()
        .orElse(null);
    return new TokenMetricSnapshot(
        new TokenUsage(readType("input"), readType("output"), readType("total")),
        responseModel);
  }

  private Long readType(String type) {
    try {
      MetricResponse response = restClient.get()
          .uri(METRIC_PATH + "?tag=gen_ai.token.type:" + type)
          .retrieve()
          .body(MetricResponse.class);
      if (response == null || response.measurements() == null
          || response.measurements().isEmpty()) {
        return null;
      }
      double value = response.measurements().stream().mapToDouble(Measurement::value).sum();
      return Math.round(value);
    } catch (RestClientException exception) {
      return null;
    }
  }

  private record MetricResponse(
      String name,
      List<Measurement> measurements,
      List<AvailableTag> availableTags) {}

  private record Measurement(String statistic, double value) {}

  private record AvailableTag(String tag, List<String> values) {}
}
