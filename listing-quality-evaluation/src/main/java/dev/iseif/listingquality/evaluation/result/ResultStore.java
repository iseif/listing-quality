package dev.iseif.listingquality.evaluation.result;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ResultStore {

  private static final String MANIFEST_FILE = "manifest.json";
  private static final String SAMPLES_FILE = "samples.jsonl";

  private final ObjectMapper objectMapper;
  private final Path root;

  public ResultStore(ObjectMapper objectMapper, Path root) {
    this.objectMapper = objectMapper;
    this.root = root;
  }

  public Path begin(EvaluationManifest manifest, boolean overwrite) {
    Path experiment = root.resolve(manifest.experiment());
    Path completed = experiment.resolve(manifest.candidate());
    Path partial = experiment.resolve(manifest.candidate() + ".partial");
    try {
      Files.createDirectories(experiment);
      if (Files.exists(completed) && !overwrite) {
        throw new IllegalStateException(
            "Completed candidate run already exists: " + manifest.candidate());
      }
      if (Files.exists(partial) && !overwrite) {
        throw new IllegalStateException(
            "Partial candidate run already exists: " + manifest.candidate());
      }
      if (overwrite) {
        deleteTree(completed);
        deleteTree(partial);
      }
      Files.createDirectory(partial);
      writeManifest(partial, manifest);
      Files.createFile(partial.resolve(SAMPLES_FILE));
      return partial;
    } catch (IOException exception) {
      throw new IllegalStateException("Candidate run could not be initialized", exception);
    }
  }

  public void append(Path partial, SampleResult sample) {
    requirePartial(partial);
    String line = objectMapper.writeValueAsString(sample) + System.lineSeparator();
    try {
      Files.writeString(partial.resolve(SAMPLES_FILE), line, StandardCharsets.UTF_8,
          StandardOpenOption.APPEND);
    } catch (IOException exception) {
      throw new IllegalStateException("Sample result could not be persisted", exception);
    }
  }

  public Path complete(Path partial, EvaluationManifest completedManifest) {
    requirePartial(partial);
    if (completedManifest.completedAt() == null) {
      throw new IllegalArgumentException("Completed manifest requires a completion timestamp");
    }
    writeManifest(partial, completedManifest);
    String name = partial.getFileName().toString();
    Path completed = partial.resolveSibling(name.substring(0, name.length() - ".partial".length()));
    try {
      return Files.move(partial, completed, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException exception) {
      throw new IllegalStateException("Atomic candidate run finalization is not supported", exception);
    } catch (IOException exception) {
      throw new IllegalStateException("Candidate run could not be finalized", exception);
    }
  }

  public CandidateRun load(Path candidateDirectory) {
    if (candidateDirectory.getFileName().toString().endsWith(".partial")) {
      throw new IllegalArgumentException("Partial candidate runs cannot be compared");
    }
    try {
      EvaluationManifest manifest = objectMapper.readValue(
          Files.readAllBytes(candidateDirectory.resolve(MANIFEST_FILE)), EvaluationManifest.class);
      List<SampleResult> samples = new ArrayList<>();
      for (String line : Files.readAllLines(candidateDirectory.resolve(SAMPLES_FILE))) {
        if (!line.isBlank()) {
          samples.add(objectMapper.readValue(line, SampleResult.class));
        }
      }
      return new CandidateRun(manifest, samples);
    } catch (IOException | RuntimeException exception) {
      throw new IllegalStateException("Completed candidate run could not be read", exception);
    }
  }

  public List<CandidateRun> loadCompleted(String experiment) {
    Path experimentDirectory = root.resolve(experiment);
    if (!Files.isDirectory(experimentDirectory)) {
      return List.of();
    }
    try (var children = Files.list(experimentDirectory)) {
      return children
          .filter(Files::isDirectory)
          .filter(path -> !path.getFileName().toString().endsWith(".partial"))
          .filter(path -> Files.exists(path.resolve(MANIFEST_FILE)))
          .sorted(Comparator.comparing(path -> path.getFileName().toString()))
          .map(this::load)
          .toList();
    } catch (IOException exception) {
      throw new IllegalStateException("Completed candidate runs could not be listed", exception);
    }
  }

  private void writeManifest(Path directory, EvaluationManifest manifest) {
    try {
      Files.writeString(directory.resolve(MANIFEST_FILE),
          objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(manifest),
          StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new IllegalStateException("Candidate manifest could not be persisted", exception);
    }
  }

  private void requirePartial(Path path) {
    if (path == null || !path.getFileName().toString().endsWith(".partial")
        || !Files.isDirectory(path)) {
      throw new IllegalArgumentException("Expected an existing partial candidate directory");
    }
  }

  private void deleteTree(Path path) throws IOException {
    if (!Files.exists(path)) {
      return;
    }
    try (var descendants = Files.walk(path)) {
      for (Path entry : descendants.sorted(Comparator.reverseOrder()).toList()) {
        Files.delete(entry);
      }
    }
  }
}
