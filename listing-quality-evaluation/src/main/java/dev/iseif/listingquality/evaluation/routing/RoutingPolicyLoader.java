package dev.iseif.listingquality.evaluation.routing;

import dev.iseif.listingquality.evaluation.dataset.EvaluationCase;
import dev.iseif.listingquality.evaluation.dataset.EvaluationDataset;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

@Component
public final class RoutingPolicyLoader {

  private static final int SUPPORTED_SCHEMA_VERSION = 1;

  private final ObjectMapper objectMapper;

  public RoutingPolicyLoader(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public RoutingPolicy load(String policyVersion, EvaluationDataset dataset) {
    String resource = "routing-policies/%s.json".formatted(policyVersion);
    try {
      return load(new ClassPathResource(resource).getContentAsByteArray(), dataset);
    } catch (IOException exception) {
      throw new IllegalArgumentException(
          "Routing-policy resource could not be read: " + policyVersion);
    }
  }

  public RoutingPolicy load(Path policyPath, EvaluationDataset dataset) {
    try {
      return load(Files.readAllBytes(policyPath), dataset);
    } catch (IOException exception) {
      throw new IllegalArgumentException("Routing-policy file could not be read");
    }
  }

  public RoutingPolicy fromDataset(EvaluationDataset dataset) {
    Map<String, RoutingPolicyCase> cases = dataset.cases().stream()
        .collect(Collectors.toMap(
            EvaluationCase::id,
            evaluationCase -> new RoutingPolicyCase(
                evaluationCase.id(),
                evaluationCase.expectations().requiresHumanReview(),
                "Dataset expectation compatibility policy"),
            (left, right) -> left,
            LinkedHashMap::new));
    return new RoutingPolicy(
        dataset.manifest().rubricVersion(), dataset.checksum(), cases);
  }

  private RoutingPolicy load(byte[] bytes, EvaluationDataset dataset) {
    RoutingPolicyDocument document;
    try {
      document = objectMapper.readValue(bytes, RoutingPolicyDocument.class);
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("Routing policy contains invalid JSON");
    }
    validateDocument(document);

    Map<String, RoutingPolicyCase> cases = new LinkedHashMap<>();
    for (RoutingPolicyCase policyCase : document.cases()) {
      validateCase(policyCase);
      if (cases.putIfAbsent(policyCase.caseId(), policyCase) != null) {
        throw new IllegalArgumentException(
            "Duplicate routing-policy case ID: " + policyCase.caseId());
      }
    }
    validateCoverage(cases.keySet(), dataset);
    return new RoutingPolicy(document.policyVersion(), checksum(bytes), cases);
  }

  private void validateDocument(RoutingPolicyDocument document) {
    if (document == null) {
      throw new IllegalArgumentException("Routing policy must not be null");
    }
    if (document.schemaVersion() != SUPPORTED_SCHEMA_VERSION) {
      throw new IllegalArgumentException(
          "Unsupported routing-policy schema version: " + document.schemaVersion());
    }
    if (document.policyVersion() == null || document.policyVersion().isBlank()) {
      throw new IllegalArgumentException("Routing-policy version must not be blank");
    }
  }

  private void validateCase(RoutingPolicyCase policyCase) {
    if (policyCase == null || policyCase.caseId() == null || policyCase.caseId().isBlank()) {
      throw new IllegalArgumentException("Routing-policy case ID must not be blank");
    }
    if (policyCase.reason() == null || policyCase.reason().isBlank()) {
      throw new IllegalArgumentException(
          "Routing-policy reason must not be blank for " + policyCase.caseId());
    }
  }

  private void validateCoverage(Set<String> actualIds, EvaluationDataset dataset) {
    Set<String> expectedIds = dataset.cases().stream()
        .map(EvaluationCase::id)
        .collect(Collectors.toCollection(LinkedHashSet::new));
    Set<String> unknown = new LinkedHashSet<>(actualIds);
    unknown.removeAll(expectedIds);
    if (!unknown.isEmpty()) {
      throw new IllegalArgumentException("Routing policy contains unknown case IDs: " + unknown);
    }
    Set<String> missing = new LinkedHashSet<>(expectedIds);
    missing.removeAll(actualIds);
    if (!missing.isEmpty()) {
      throw new IllegalArgumentException("Routing policy is missing dataset case IDs: " + missing);
    }
  }

  private String checksum(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }
}
