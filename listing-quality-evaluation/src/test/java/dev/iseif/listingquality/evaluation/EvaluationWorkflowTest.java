package dev.iseif.listingquality.evaluation;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.iseif.listingquality.evaluation.command.CollectCommand;
import dev.iseif.listingquality.evaluation.command.CompareCommand;
import dev.iseif.listingquality.evaluation.command.EvaluationCommandDispatcher;
import dev.iseif.listingquality.evaluation.compare.SampleAssessment;
import dev.iseif.listingquality.evaluation.compare.SemanticStatus;
import dev.iseif.listingquality.evaluation.dataset.DatasetLoader;
import dev.iseif.listingquality.evaluation.dataset.EvaluationDataset;
import dev.iseif.listingquality.evaluation.grade.HumanReviewOutcome;
import dev.iseif.listingquality.evaluation.result.RuntimeEnvironment;
import dev.iseif.listingquality.evaluation.result.SourceRevision;
import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationWorkflowTest {

  @TempDir
  Path temporaryDirectory;

  private HttpServer server;
  private URI baseUrl;
  private final Map<String, AtomicInteger> metricReads = new ConcurrentHashMap<>();

  @BeforeEach
  void startFakeService() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/actuator/health", exchange -> json(exchange, 200,
        "{\"status\":\"UP\"}"));
    server.createContext("/api/listings/review", exchange -> json(exchange, 200, """
        {"qualityScore":50,"missingFields":["author","isbn"],"issues":[],
         "suggestions":["Add missing details"],"requiresHumanReview":false}
        """));
    server.createContext("/actuator/metrics/gen_ai.client.token.usage", this::metrics);
    server.start();
    baseUrl = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
  }

  @AfterEach
  void stopFakeService() {
    server.stop(0);
  }

  @Test
  void collectsAndComparesThroughTheRealOfflineCommandDispatcher() {
    ObjectMapper objectMapper = new ObjectMapper();
    DatasetLoader datasetLoader = new DatasetLoader(
        objectMapper, Validation.buildDefaultValidatorFactory().getValidator());
    EvaluationCommandDispatcher dispatcher = new EvaluationCommandDispatcher(
        objectMapper,
        datasetLoader,
        RestClient.builder(),
        () -> null,
        () -> new SourceRevision("abc", true, "d".repeat(64)),
        (runtime, version) -> new RuntimeEnvironment(
            runtime, version, "test-os", "test-arch", "test-cpu", 1024, "25"),
        Clock.systemUTC());

    dispatcher.dispatch(new CollectCommand(
        "listing-quality-v1", "fake-model", "fake", "fake-model", "fake-runtime",
        "1.0", new BigDecimal("0.2"), 800, baseUrl, 3, Duration.ofSeconds(5),
        temporaryDirectory, false));
    EvaluationDataset dataset = datasetLoader.load("listing-quality-v1");
    Path semanticSource = writeSemanticSource(objectMapper, dataset);
    Path comparisonDirectory = temporaryDirectory.resolve(
        "listing-quality-v1/comparison-routing-v2");
    dispatcher.dispatch(new CompareCommand(
        "listing-quality-v1", temporaryDirectory, comparisonDirectory,
        "listing-quality-routing-v2", semanticSource, false, false));

    assertThat(temporaryDirectory.resolve(
        "listing-quality-v1/fake-model/manifest.json")).isRegularFile();
    assertThat(temporaryDirectory.resolve(
        "listing-quality-v1/fake-model/samples.jsonl"))
        .content(StandardCharsets.UTF_8)
        .hasLineCount(36);
    assertThat(comparisonDirectory.resolve("comparison.json")).isRegularFile();
    assertThat(comparisonDirectory.resolve("comparison.md")).isRegularFile();
    assertThat(comparisonDirectory.resolve("comparison.json"))
        .content(StandardCharsets.UTF_8)
        .contains(
            "\"schemaVersion\" : 2",
            "\"routingPolicyVersion\" : \"listing-quality-routing-v2\"",
            "\"qualityStatus\" : \"PASS\"")
        .doesNotContain("\"eligibility\"");
  }

  private Path writeSemanticSource(ObjectMapper objectMapper, EvaluationDataset dataset) {
    List<SampleAssessment> assessments = new ArrayList<>();
    dataset.cases().forEach(evaluationCase -> {
      for (int repetition = 1; repetition <= 3; repetition++) {
        assessments.add(new SampleAssessment(
            "fake-model", evaluationCase.id(), repetition, true, true,
            HumanReviewOutcome.TRUE_NEGATIVE, List.of(),
            SemanticStatus.EVALUATED, 85, "covered"));
      }
    });
    Path source = temporaryDirectory.resolve("semantic-source.json");
    try {
      Files.write(source, objectMapper.writeValueAsBytes(Map.of(
          "schemaVersion", 1,
          "experiment", "listing-quality-v1",
          "datasetVersion", dataset.manifest().datasetVersion(),
          "datasetChecksum", dataset.checksum(),
          "rubricVersion", dataset.manifest().rubricVersion(),
          "candidates", List.of(),
          "assessments", assessments,
          "limitations", List.of())));
      return source;
    } catch (IOException exception) {
      throw new IllegalStateException("Test semantic source could not be written", exception);
    }
  }

  private void metrics(HttpExchange exchange) throws IOException {
    String query = exchange.getRequestURI().getRawQuery();
    if (query == null) {
      json(exchange, 200, """
          {"name":"gen_ai.client.token.usage","measurements":[],"availableTags":[
            {"tag":"gen_ai.response.model","values":["fake-model"]},
            {"tag":"gen_ai.token.type","values":["input","output","total"]}
          ]}
          """);
      return;
    }
    String type = query.substring(query.lastIndexOf(':') + 1);
    int read = metricReads.computeIfAbsent(type, ignored -> new AtomicInteger()).getAndIncrement();
    long[][] values = {{0, 0, 0}, {10, 5, 15}, {110, 55, 165}};
    int typeIndex = switch (type) {
      case "input" -> 0;
      case "output" -> 1;
      default -> 2;
    };
    json(exchange, 200, """
        {"name":"gen_ai.client.token.usage",
         "measurements":[{"statistic":"TOTAL","value":%d}],"availableTags":[]}
        """.formatted(values[Math.min(read, 2)][typeIndex]));
  }

  private void json(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }
}
