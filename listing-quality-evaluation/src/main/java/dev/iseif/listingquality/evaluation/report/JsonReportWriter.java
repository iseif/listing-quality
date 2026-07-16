package dev.iseif.listingquality.evaluation.report;

import dev.iseif.listingquality.evaluation.compare.ScorecardReport;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class JsonReportWriter {

  private final ObjectMapper objectMapper;

  public JsonReportWriter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public Path write(ScorecardReport report, Path outputDirectory) {
    return writeReport(report, outputDirectory);
  }

  private Path writeReport(Object report, Path outputDirectory) {
    try {
      Files.createDirectories(outputDirectory);
      Path output = outputDirectory.resolve("comparison.json");
      Files.writeString(output,
          objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report),
          StandardCharsets.UTF_8);
      return output;
    } catch (IOException exception) {
      throw new IllegalStateException("JSON comparison report could not be written", exception);
    }
  }
}
