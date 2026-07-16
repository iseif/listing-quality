package dev.iseif.listingquality.evaluation.dataset;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Component
public final class DatasetLoader {

  private static final int SUPPORTED_SCHEMA_VERSION = 1;
  private static final int REQUIRED_CASE_COUNT = 12;

  private final ObjectMapper objectMapper;
  private final Validator validator;

  public DatasetLoader(ObjectMapper objectMapper, Validator validator) {
    this.objectMapper = objectMapper;
    this.validator = validator;
  }

  public EvaluationDataset load(String datasetVersion) {
    String root = "datasets/%s/".formatted(datasetVersion);
    try {
      byte[] manifest = new ClassPathResource(root + "manifest.json").getContentAsByteArray();
      byte[] cases = new ClassPathResource(root + "cases.jsonl").getContentAsByteArray();
      return load(manifest, cases);
    } catch (IOException exception) {
      throw new IllegalArgumentException("Dataset resources could not be read: " + datasetVersion);
    }
  }

  public EvaluationDataset load(Path manifestPath, Path casesPath) {
    try {
      return load(Files.readAllBytes(manifestPath), Files.readAllBytes(casesPath));
    } catch (IOException exception) {
      throw new IllegalArgumentException("Dataset files could not be read");
    }
  }

  private EvaluationDataset load(byte[] manifestBytes, byte[] casesBytes) {
    DatasetManifest manifest;
    List<EvaluationCase> cases = new ArrayList<>();
    try {
      manifest = objectMapper.readValue(manifestBytes, DatasetManifest.class);
      for (String line : new String(casesBytes, StandardCharsets.UTF_8).lines().toList()) {
        if (!line.isBlank()) {
          cases.add(objectMapper.readValue(line, EvaluationCase.class));
        }
      }
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("Dataset contains invalid JSON");
    }

    validate(manifest, cases);
    return new EvaluationDataset(manifest, cases, checksum(manifestBytes, casesBytes));
  }

  private void validate(DatasetManifest manifest, List<EvaluationCase> cases) {
    validateBean("manifest", manifest);
    if (manifest.schemaVersion() != SUPPORTED_SCHEMA_VERSION) {
      throw new IllegalArgumentException(
          "Unsupported dataset schema version: " + manifest.schemaVersion());
    }

    Set<String> ids = new LinkedHashSet<>();
    for (EvaluationCase evaluationCase : cases) {
      validateBean("case", evaluationCase);
      if (!ids.add(evaluationCase.id())) {
        throw new IllegalArgumentException("Duplicate evaluation case ID: " + evaluationCase.id());
      }
      ScoreRange range = evaluationCase.expectations().acceptableScore();
      if (range.minimum() > range.maximum()) {
        throw new IllegalArgumentException(
            "Case %s score range minimum must not exceed maximum".formatted(evaluationCase.id()));
      }
    }

    if (cases.size() != REQUIRED_CASE_COUNT) {
      throw new IllegalArgumentException(
          "Expected 12 evaluation cases but found " + cases.size());
    }
    if (!ids.contains(manifest.warmupCaseId())) {
      throw new IllegalArgumentException(
          "Warm-up case does not exist: " + manifest.warmupCaseId());
    }
  }

  private <T> void validateBean(String prefix, T bean) {
    Set<ConstraintViolation<T>> violations = validator.validate(bean);
    if (!violations.isEmpty()) {
      String message = violations.stream()
          .map(violation -> prefix + "." + violation.getPropertyPath() + " " + violation.getMessage())
          .sorted()
          .findFirst()
          .orElseThrow();
      throw new IllegalArgumentException(message);
    }
  }

  private String checksum(byte[] manifest, byte[] cases) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(manifest);
      digest.update((byte) '\n');
      digest.update(cases);
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }
}
