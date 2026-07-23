package dev.iseif.listingquality.enrichment;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Telemetry collection is not opt-in. Only exporting it is.
 *
 * <p>This test boots without the observability profile, which is how the application runs by
 * default, and asserts that metrics are still collected and scrapable while nothing is pushed to
 * an external system.
 */
@SpringBootTest(
    classes = ListingQualityEnrichmentApplication.class,
    properties = {
        "spring.ai.google.genai.api-key=test-gemini-key",
        "listing-quality.enrichment.providers.gemini.api-key=test-gemini-key",
        "listing-quality.enrichment.catalog.google-books.api-key=test-google-books-key"
    })
class DefaultTelemetryConfigurationTest {

  @Autowired
  private ApplicationContext context;

  @Autowired
  private ObservationRegistry observationRegistry;

  @Autowired
  private Environment environment;

  @Test
  void collectsAndExposesMetricsWithoutTheObservabilityProfile() {
    assertThat(environment.getActiveProfiles()).doesNotContain("observability");
    assertThat(context.getBeansOfType(PrometheusMeterRegistry.class)).hasSize(1);
    assertThat(observationRegistry).isNotNull();
    assertThat(environment.getProperty("management.endpoints.web.exposure.include"))
        .contains("prometheus");
  }

  @Test
  void keepsExportDisabledUntilTheObservabilityProfileIsActive() {
    assertThat(environment.getProperty(
        "management.opentelemetry.tracing.export.otlp.enabled", Boolean.class))
        .isNotNull()
        .isFalse();
    assertThat(environment.getProperty("management.otlp.metrics.export.enabled", Boolean.class))
        .isNotNull()
        .isFalse();
  }
}
