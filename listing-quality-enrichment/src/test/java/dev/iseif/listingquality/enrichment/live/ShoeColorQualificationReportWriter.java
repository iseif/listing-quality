package dev.iseif.listingquality.enrichment.live;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

final class ShoeColorQualificationReportWriter {

  private final ObjectMapper objectMapper;

  ShoeColorQualificationReportWriter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  Path write(String provider, String model, ShoeColorQualificationReport report) {
    Path directory = Path.of("target", "shoe-color-evaluation");
    Path destination = directory.resolve(safe(provider) + "-" + safe(model) + ".json");
    try {
      Files.createDirectories(directory);
      objectMapper.writerWithDefaultPrettyPrinter().writeValue(destination.toFile(), report);
      return destination;
    } catch (IOException exception) {
      throw new IllegalStateException("Could not write shoe color qualification report", exception);
    }
  }

  private String safe(String value) {
    return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "-");
  }
}
