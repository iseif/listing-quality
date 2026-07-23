package dev.iseif.listingquality.enrichment.config;

import io.micrometer.context.ContextRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.assertThat;

class EnrichmentExecutorConfigurationTest {

  @Test
  void propagatesMicrometerContextIntoVirtualThreadTasks() throws Exception {
    ThreadLocal<String> traceContext = new ThreadLocal<>();
    String accessorKey = "enrichment-executor-test";
    ContextRegistry.getInstance().registerThreadLocalAccessor(accessorKey, traceContext);

    try (ExecutorService executor = new EnrichmentModelConfiguration().enrichmentExecutor()) {
      traceContext.set("trace-123");

      assertThat(executor.submit(traceContext::get).get()).isEqualTo("trace-123");
    } finally {
      traceContext.remove();
      ContextRegistry.getInstance().removeThreadLocalAccessor(accessorKey);
    }
  }
}
