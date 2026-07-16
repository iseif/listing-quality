package dev.iseif.listingquality.evaluation.command;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvaluationCommandParserTest {

  private final EvaluationCommandParser parser = new EvaluationCommandParser();

  @Test
  void parsesCollectWithDefaults() {
    EvaluationCommand command = parser.parse(new String[] {
        "collect",
        "--experiment=listing-quality-v1",
        "--candidate=openai-gpt-5.6",
        "--provider=openai",
        "--model=gpt-5.6",
        "--runtime=openai-api",
        "--runtime-version=2026-07-14"
    });

    assertThat(command).isEqualTo(new CollectCommand(
        "listing-quality-v1",
        "openai-gpt-5.6",
        "openai",
        "gpt-5.6",
        "openai-api",
        "2026-07-14",
        new BigDecimal("0.2"),
        800,
        URI.create("http://localhost:8080"),
        3,
        Duration.ofMinutes(2),
        Path.of("target/evaluation-runs"),
        false));
  }

  @Test
  void parsesCompareWithDefaults() {
    EvaluationCommand command = parser.parse(new String[] {
        "compare", "--experiment=listing-quality-v1"
    });

    assertThat(command).isEqualTo(new CompareCommand(
        "listing-quality-v1",
        Path.of("target/evaluation-runs"),
        Path.of("target/evaluation-runs/listing-quality-v1/comparison"),
        null,
        null,
        false,
        false));
  }

  @Test
  void parsesRoutingPolicyAndSemanticSource() {
    CompareCommand command = (CompareCommand) parser.parse(new String[] {
        "compare",
        "--experiment=listing-quality-v1",
        "--routing-policy=listing-quality-routing-v2",
        "--semantic-source=target/evaluation-runs/listing-quality-v1/comparison/comparison.json",
        "--output-directory=target/evaluation-runs/listing-quality-v1/comparison-routing-v2"
    });

    assertThat(command.routingPolicy()).isEqualTo("listing-quality-routing-v2");
    assertThat(command.semanticSource()).isEqualTo(Path.of(
        "target/evaluation-runs/listing-quality-v1/comparison/comparison.json"));
    assertThat(command.semantic()).isFalse();
  }

  @Test
  void rejectsSemanticSourceWithLiveOrResumedSemanticEvaluation() {
    assertThatThrownBy(() -> parser.parse(new String[] {
        "compare", "--experiment=listing-quality-v1",
        "--semantic-source=comparison.json", "--semantic=true"
    })).isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "--semantic-source cannot be combined with --semantic or --resume-semantic");

    assertThatThrownBy(() -> parser.parse(new String[] {
        "compare", "--experiment=listing-quality-v1",
        "--semantic-source=comparison.json", "--resume-semantic=true"
    })).isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "--semantic-source cannot be combined with --semantic or --resume-semantic");
  }

  @Test
  void parsesSemanticResume() {
    CompareCommand command = (CompareCommand) parser.parse(new String[] {
        "compare",
        "--experiment=listing-quality-v1",
        "--semantic=true",
        "--resume-semantic=true"
    });

    assertThat(command.semantic()).isTrue();
    assertThat(command.resumeSemantic()).isTrue();
  }

  @Test
  void parsesExplicitCollectValues() {
    CollectCommand command = (CollectCommand) parser.parse(new String[] {
        "collect",
        "--experiment=exp",
        "--candidate=candidate",
        "--provider=ollama",
        "--model=gemma4:e4b",
        "--runtime=ollama",
        "--runtime-version=0.9.6",
        "--temperature=0.1",
        "--token-limit=512",
        "--base-url=http://127.0.0.1:9090",
        "--repetitions=2",
        "--timeout=PT30S",
        "--output-root=build/runs",
        "--overwrite=true"
    });

    assertThat(command.temperature()).isEqualByComparingTo("0.1");
    assertThat(command.tokenLimit()).isEqualTo(512);
    assertThat(command.baseUrl()).isEqualTo(URI.create("http://127.0.0.1:9090"));
    assertThat(command.repetitions()).isEqualTo(2);
    assertThat(command.timeout()).isEqualTo(Duration.ofSeconds(30));
    assertThat(command.outputRoot()).isEqualTo(Path.of("build/runs"));
    assertThat(command.overwrite()).isTrue();
  }

  @Test
  void rejectsMissingRequiredOption() {
    assertThatThrownBy(() -> parser.parse(new String[] {
        "collect", "--experiment=listing-quality-v1"
    })).isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Missing required option --candidate");
  }

  @Test
  void rejectsDuplicateOption() {
    assertThatThrownBy(() -> parser.parse(new String[] {
        "compare", "--experiment=one", "--experiment=two"
    })).isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Duplicate option --experiment");
  }

  @Test
  void rejectsUnknownOption() {
    assertThatThrownBy(() -> parser.parse(new String[] {
        "compare", "--experiment=one", "--ranking=true"
    })).isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unknown option --ranking for compare");
  }

  @Test
  void rejectsInvalidNumericAndUriValues() {
    String[] required = {
        "collect", "--experiment=exp", "--candidate=c", "--provider=p",
        "--model=m", "--runtime=r", "--runtime-version=v"
    };

    assertThatThrownBy(() -> parser.parse(append(required, "--repetitions=zero")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid value for --repetitions: zero");
    assertThatThrownBy(() -> parser.parse(append(required, "--base-url=not a uri")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid value for --base-url: not a uri");
  }

  private String[] append(String[] values, String value) {
    String[] result = java.util.Arrays.copyOf(values, values.length + 1);
    result[result.length - 1] = value;
    return result;
  }
}
