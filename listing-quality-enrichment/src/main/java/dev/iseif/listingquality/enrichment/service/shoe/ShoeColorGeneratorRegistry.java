package dev.iseif.listingquality.enrichment.service.shoe;

import java.util.Map;
import java.util.TreeSet;

public record ShoeColorGeneratorRegistry(Map<String, ShoeColorGenerator> byProviderId) {

  public ShoeColorGeneratorRegistry {
    byProviderId = Map.copyOf(byProviderId);
  }

  public ShoeColorGenerator require(String providerId) {
    ShoeColorGenerator generator = byProviderId.get(providerId);
    if (generator == null) {
      throw new IllegalStateException(
          "No shoe color generator is registered for provider '" + providerId
              + "'. Registered providers: " + new TreeSet<>(byProviderId.keySet()));
    }
    return generator;
  }
}
