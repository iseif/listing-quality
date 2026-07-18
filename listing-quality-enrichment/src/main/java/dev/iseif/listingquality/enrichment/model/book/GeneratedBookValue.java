package dev.iseif.listingquality.enrichment.model.book;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

public record GeneratedBookValue(
    @NotNull @JsonPropertyDescription("The populated value slot: TEXT, ITEMS, or INTEGER")
    GeneratedBookValueKind kind,
    @NotNull @JsonPropertyDescription("The value for TEXT; otherwise an empty string")
    String text,
    @NotNull @JsonPropertyDescription("The value for ITEMS; otherwise an empty list")
    List<String> items,
    @NotNull @JsonPropertyDescription("The value for INTEGER; otherwise zero")
    Integer integer) {

  public GeneratedBookValue {
    items = items == null ? null : List.copyOf(items);
  }

  public static GeneratedBookValue from(JsonNode value) {
    if (value == null) {
      return null;
    }
    if (value.isString()) {
      return new GeneratedBookValue(
          GeneratedBookValueKind.TEXT, value.asString(), List.of(), 0);
    }
    if (value.isIntegralNumber()) {
      return new GeneratedBookValue(
          GeneratedBookValueKind.INTEGER, "", List.of(), value.asInt());
    }
    if (value.isArray()) {
      List<String> items = new ArrayList<>();
      for (JsonNode item : value) {
        if (!item.isString()) {
          throw new IllegalArgumentException("Generated list values must contain text only");
        }
        items.add(item.asString());
      }
      return new GeneratedBookValue(GeneratedBookValueKind.ITEMS, "", items, 0);
    }
    throw new IllegalArgumentException("Unsupported generated book value");
  }
}
