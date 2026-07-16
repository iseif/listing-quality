package dev.iseif.listingquality.evaluation.command;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
public final class EvaluationCommandParser {

  private static final Set<String> COLLECT_OPTIONS = Set.of(
      "experiment", "candidate", "provider", "model", "runtime", "runtime-version",
      "temperature", "token-limit", "base-url", "repetitions", "timeout", "output-root",
      "overwrite");
  private static final Set<String> COMPARE_OPTIONS = Set.of(
      "experiment", "input-root", "output-directory", "routing-policy", "semantic-source",
      "semantic", "resume-semantic");

  public EvaluationCommand parse(String[] arguments) {
    if (arguments.length == 0) {
      throw new IllegalArgumentException("Expected command: collect or compare");
    }
    String command = arguments[0];
    Map<String, String> options = parseOptions(arguments);
    return switch (command) {
      case "collect" -> parseCollect(options);
      case "compare" -> parseCompare(options);
      default -> throw new IllegalArgumentException("Unknown command: " + command);
    };
  }

  private CollectCommand parseCollect(Map<String, String> options) {
    rejectUnknown("collect", options, COLLECT_OPTIONS);
    return new CollectCommand(
        required(options, "experiment"),
        required(options, "candidate"),
        required(options, "provider"),
        required(options, "model"),
        required(options, "runtime"),
        required(options, "runtime-version"),
        decimal(options, "temperature", "0.2"),
        positiveInteger(options, "token-limit", "800"),
        uri(options, "base-url", "http://localhost:8080"),
        positiveInteger(options, "repetitions", "3"),
        duration(options, "timeout", "PT2M"),
        Path.of(options.getOrDefault("output-root", "target/evaluation-runs")),
        bool(options, "overwrite", false));
  }

  private CompareCommand parseCompare(Map<String, String> options) {
    rejectUnknown("compare", options, COMPARE_OPTIONS);
    String experiment = required(options, "experiment");
    Path inputRoot = Path.of(options.getOrDefault("input-root", "target/evaluation-runs"));
    Path output = options.containsKey("output-directory")
        ? Path.of(options.get("output-directory"))
        : inputRoot.resolve(experiment).resolve("comparison");
    boolean semantic = bool(options, "semantic", false);
    boolean resumeSemantic = bool(options, "resume-semantic", false);
    String routingPolicy = options.get("routing-policy");
    Path semanticSource = options.containsKey("semantic-source")
        ? Path.of(options.get("semantic-source"))
        : null;
    if (semanticSource != null && (semantic || resumeSemantic)) {
      throw new IllegalArgumentException(
          "--semantic-source cannot be combined with --semantic or --resume-semantic");
    }
    if (resumeSemantic && !semantic) {
      throw new IllegalArgumentException("--resume-semantic=true requires --semantic=true");
    }
    return new CompareCommand(
        experiment, inputRoot, output, routingPolicy, semanticSource, semantic, resumeSemantic);
  }

  private Map<String, String> parseOptions(String[] arguments) {
    Map<String, String> options = new LinkedHashMap<>();
    for (int index = 1; index < arguments.length; index++) {
      String argument = arguments[index];
      if (!argument.startsWith("--") || !argument.contains("=")) {
        throw new IllegalArgumentException("Expected option in --key=value form: " + argument);
      }
      int separator = argument.indexOf('=');
      String key = argument.substring(2, separator);
      String value = argument.substring(separator + 1);
      if (key.isBlank() || value.isBlank()) {
        throw new IllegalArgumentException("Option name and value must not be blank: " + argument);
      }
      if (options.putIfAbsent(key, value) != null) {
        throw new IllegalArgumentException("Duplicate option --" + key);
      }
    }
    return options;
  }

  private void rejectUnknown(String command, Map<String, String> options, Set<String> allowed) {
    options.keySet().stream()
        .filter(option -> !allowed.contains(option))
        .findFirst()
        .ifPresent(option -> {
          throw new IllegalArgumentException("Unknown option --" + option + " for " + command);
        });
  }

  private String required(Map<String, String> options, String key) {
    String value = options.get(key);
    if (value == null) {
      throw new IllegalArgumentException("Missing required option --" + key);
    }
    return value;
  }

  private int positiveInteger(Map<String, String> options, String key, String defaultValue) {
    String value = options.getOrDefault(key, defaultValue);
    try {
      int parsed = Integer.parseInt(value);
      if (parsed < 1) {
        throw new NumberFormatException();
      }
      return parsed;
    } catch (NumberFormatException exception) {
      throw invalid(key, value);
    }
  }

  private BigDecimal decimal(Map<String, String> options, String key, String defaultValue) {
    String value = options.getOrDefault(key, defaultValue);
    try {
      return new BigDecimal(value);
    } catch (NumberFormatException exception) {
      throw invalid(key, value);
    }
  }

  private URI uri(Map<String, String> options, String key, String defaultValue) {
    String value = options.getOrDefault(key, defaultValue);
    try {
      URI uri = URI.create(value);
      if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
          || uri.getHost() == null) {
        throw invalid(key, value);
      }
      return uri;
    } catch (IllegalArgumentException exception) {
      if (exception.getMessage() != null && exception.getMessage().startsWith("Invalid value")) {
        throw exception;
      }
      throw invalid(key, value);
    }
  }

  private Duration duration(Map<String, String> options, String key, String defaultValue) {
    String value = options.getOrDefault(key, defaultValue);
    try {
      Duration duration = Duration.parse(value);
      if (duration.isZero() || duration.isNegative()) {
        throw invalid(key, value);
      }
      return duration;
    } catch (DateTimeParseException exception) {
      throw invalid(key, value);
    }
  }

  private boolean bool(Map<String, String> options, String key, boolean defaultValue) {
    String value = options.get(key);
    if (value == null) {
      return defaultValue;
    }
    if ("true".equalsIgnoreCase(value)) {
      return true;
    }
    if ("false".equalsIgnoreCase(value)) {
      return false;
    }
    throw invalid(key, value);
  }

  private IllegalArgumentException invalid(String key, String value) {
    return new IllegalArgumentException("Invalid value for --%s: %s".formatted(key, value));
  }
}
