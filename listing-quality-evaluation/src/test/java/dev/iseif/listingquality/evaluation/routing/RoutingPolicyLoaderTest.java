package dev.iseif.listingquality.evaluation.routing;

import dev.iseif.listingquality.evaluation.dataset.DatasetLoader;
import dev.iseif.listingquality.evaluation.dataset.EvaluationDataset;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoutingPolicyLoaderTest {

  @TempDir
  Path temporaryDirectory;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final EvaluationDataset dataset = new DatasetLoader(
      objectMapper, Validation.buildDefaultValidatorFactory().getValidator())
      .load("listing-quality-v1");
  private final RoutingPolicyLoader loader = new RoutingPolicyLoader(objectMapper);

  @Test
  void loadsTheApprovedPolicyWithExactDatasetCoverage() {
    RoutingPolicy policy = loader.load("listing-quality-routing-v2", dataset);

    assertThat(policy.policyVersion()).isEqualTo("listing-quality-routing-v2");
    assertThat(policy.cases()).hasSize(12);
    assertThat(policy.requiresHumanReview("prompt-injection-camera")).isTrue();
    assertThat(policy.requiresHumanReview("vague-wireless-headphones")).isFalse();
    assertThat(policy.checksum()).matches("[0-9a-f]{64}");
  }

  @Test
  void rejectsDuplicateCaseIds() throws Exception {
    Path policy = writePolicy("""
        {"schemaVersion":1,"policyVersion":"test-policy","cases":[
          {"caseId":"complete-running-shoes","requiresHumanReview":false,"reason":"first"},
          {"caseId":"complete-running-shoes","requiresHumanReview":true,"reason":"second"}
        ]}
        """);

    assertThatThrownBy(() -> loader.load(policy, dataset))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Duplicate routing-policy case ID");
  }

  @Test
  void rejectsMissingDatasetCases() throws Exception {
    Path policy = writePolicy("""
        {"schemaVersion":1,"policyVersion":"test-policy","cases":[
          {"caseId":"complete-running-shoes","requiresHumanReview":false,"reason":"ordinary"}
        ]}
        """);

    assertThatThrownBy(() -> loader.load(policy, dataset))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("missing dataset case IDs");
  }

  @Test
  void rejectsUnknownCaseIds() throws Exception {
    String resource = Files.readString(Path.of(
        "src/main/resources/routing-policies/listing-quality-routing-v2.json"));
    Path policy = writePolicy(resource.replace(
        "complete-running-shoes", "unknown-listing"));

    assertThatThrownBy(() -> loader.load(policy, dataset))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown case IDs");
  }

  @Test
  void createsACompatibilityPolicyFromDatasetExpectations() {
    RoutingPolicy policy = loader.fromDataset(dataset);

    assertThat(policy.policyVersion())
        .isEqualTo(dataset.manifest().rubricVersion());
    assertThat(policy.checksum()).isEqualTo(dataset.checksum());
    assertThat(policy.requiresHumanReview("prompt-injection-camera")).isFalse();
  }

  private Path writePolicy(String json) throws Exception {
    Path path = temporaryDirectory.resolve("policy.json");
    return Files.writeString(path, json);
  }
}
