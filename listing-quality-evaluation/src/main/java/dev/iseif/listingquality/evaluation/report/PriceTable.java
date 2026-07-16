package dev.iseif.listingquality.evaluation.report;

import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public final class PriceTable {

  private final List<ModelPrice> prices;

  private PriceTable(List<ModelPrice> prices) {
    this.prices = List.copyOf(prices);
  }

  public static PriceTable load(ObjectMapper objectMapper, String resourcePath) {
    try {
      byte[] content = new ClassPathResource(resourcePath).getContentAsByteArray();
      return new PriceTable(Arrays.asList(objectMapper.readValue(content, ModelPrice[].class)));
    } catch (IOException | RuntimeException exception) {
      throw new IllegalStateException("Model price table could not be loaded", exception);
    }
  }

  public Optional<ModelPrice> find(String model) {
    return prices.stream().filter(price -> price.model().equals(model)).findFirst();
  }

  public List<ModelPrice> prices() {
    return prices;
  }
}
