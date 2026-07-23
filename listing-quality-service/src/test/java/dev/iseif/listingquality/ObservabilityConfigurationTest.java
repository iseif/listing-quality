package dev.iseif.listingquality;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "spring.profiles.active=observability",
    "management.opentelemetry.tracing.export.otlp.enabled=false"
})
class ObservabilityConfigurationTest {

  @Autowired
  private MeterRegistry meterRegistry;

  @Autowired
  private ApplicationContext context;

  @Autowired
  private ObservationRegistry observationRegistry;

  @Autowired
  private Tracer tracer;

  @Autowired
  private Environment environment;

  @Test
  void configuresPrometheusAndTracingWithoutSensitiveContent() {
    assertThat(meterRegistry).isNotNull();
    assertThat(context.getBeansOfType(PrometheusMeterRegistry.class)).hasSize(1);
    assertThat(observationRegistry).isNotNull();
    assertThat(tracer).isNotNull();
    assertThat(environment.getProperty("management.endpoints.web.exposure.include"))
        .contains("prometheus");
    // The profile's own contribution is trace export. Export is switched off for the test, so
    // assert the endpoint it configures to prove the profile was applied.
    assertThat(environment.getProperty(
        "management.opentelemetry.tracing.export.otlp.endpoint"))
        .contains("/v1/traces");
    assertThat(environment.getProperty("management.tracing.sampling.probability", Double.class))
        .isEqualTo(1.0);
    assertThat(environment.getProperty("management.otlp.metrics.export.enabled", Boolean.class))
        .isFalse();
    assertThat(environment.getProperty(
        "spring.ai.chat.observations.log-prompt", Boolean.class))
        .isNotNull()
        .isFalse();
    assertThat(environment.getProperty(
        "spring.ai.chat.observations.log-completion", Boolean.class))
        .isNotNull()
        .isFalse();
  }
}
