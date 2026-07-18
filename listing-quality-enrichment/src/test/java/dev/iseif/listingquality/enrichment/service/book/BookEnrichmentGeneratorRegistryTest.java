package dev.iseif.listingquality.enrichment.service.book;

import dev.iseif.listingquality.enrichment.model.book.GeneratedBookEnrichment;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookEnrichmentGeneratorRegistryTest {

  private final BookEnrichmentGenerator geminiGenerator = (prompt, tools) -> empty();
  private final BookEnrichmentGenerator omlxGenerator = (prompt, tools) -> empty();
  private final BookEnrichmentGeneratorRegistry registry = new BookEnrichmentGeneratorRegistry(
      Map.of("gemini", geminiGenerator, "omlx", omlxGenerator));

  @Test
  void resolvesAGeneratorByItsConfiguredProviderId() {
    assertThat(registry.require("gemini")).isSameAs(geminiGenerator);
    assertThat(registry.require("omlx")).isSameAs(omlxGenerator);
  }

  @Test
  void namesTheUnknownProviderAndTheRegisteredOnesWhenRoutingIsMisconfigured() {
    assertThatThrownBy(() -> registry.require("anthropic"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("anthropic")
        .hasMessageContaining("[gemini, omlx]");
  }

  private GeneratedBookEnrichment empty() {
    return new GeneratedBookEnrichment(List.of(), List.of(), List.of(), List.of());
  }
}
