package dev.iseif.listingquality.evaluation.collect;

import dev.iseif.listingquality.evaluation.result.SourceRevision;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

public final class GitSourceRevisionProvider implements SourceRevisionProvider {

  private final Path repository;

  public GitSourceRevisionProvider(Path repository) {
    this.repository = repository;
  }

  public static GitSourceRevisionProvider discover(Path workingDirectory) {
    String root = text(workingDirectory, "git", "rev-parse", "--show-toplevel").trim();
    if (root.isBlank()) {
      throw new IllegalStateException("Git repository root could not be discovered");
    }
    return new GitSourceRevisionProvider(Path.of(root));
  }

  @Override
  public SourceRevision capture() {
    String commit = text("git", "rev-parse", "HEAD").trim();
    String status = text("git", "status", "--porcelain", "--untracked-files=all");
    byte[] fingerprint = fingerprint();
    return new SourceRevision(commit, !status.isBlank(), sha256(fingerprint));
  }

  private byte[] fingerprint() {
    try {
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      output.write(bytes("git", "diff", "--binary", "HEAD"));
      List<String> untracked = text("git", "ls-files", "--others", "--exclude-standard")
          .lines().filter(line -> !line.isBlank()).sorted().toList();
      for (String relative : untracked) {
        output.write(relative.getBytes(StandardCharsets.UTF_8));
        output.write(0);
        output.write(Files.readAllBytes(repository.resolve(relative)));
        output.write(0);
      }
      return output.toByteArray();
    } catch (IOException exception) {
      throw new IllegalStateException("Source revision fingerprint could not be created");
    }
  }

  private String text(String... command) {
    return new String(bytes(command), StandardCharsets.UTF_8);
  }

  private byte[] bytes(String... command) {
    return bytes(repository, command);
  }

  private static String text(Path directory, String... command) {
    return new String(bytes(directory, command), StandardCharsets.UTF_8);
  }

  private static byte[] bytes(Path directory, String... command) {
    try {
      Process process = new ProcessBuilder(command)
          .directory(directory.toFile())
          .redirectErrorStream(true)
          .start();
      byte[] output = process.getInputStream().readAllBytes();
      if (process.waitFor() != 0) {
        throw new IllegalStateException("Git source revision command failed");
      }
      return output;
    } catch (IOException exception) {
      throw new IllegalStateException("Git source revision command could not start");
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Git source revision command was interrupted");
    }
  }

  private String sha256(byte[] value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }
}
