package dev.iseif.listingquality.enrichment.service.book;

import java.util.Map;
import java.util.TreeSet;

/**
 * The generators available to route to, keyed by the provider ID used in configuration.
 *
 * <p>The mapping is declared explicitly where the generators are built, so a configured provider
 * that has no generator fails at startup with a message that names it.
 */
public record BookEnrichmentGeneratorRegistry(Map<String, BookEnrichmentGenerator> byProviderId) {

  public BookEnrichmentGeneratorRegistry {
    byProviderId = Map.copyOf(byProviderId);
  }

  public BookEnrichmentGenerator require(String providerId) {
    BookEnrichmentGenerator generator = byProviderId.get(providerId);
    if (generator == null) {
      throw new IllegalStateException(
          "No enrichment generator is registered for provider '" + providerId
              + "'. Registered providers: " + new TreeSet<>(byProviderId.keySet()));
    }
    return generator;
  }
}
