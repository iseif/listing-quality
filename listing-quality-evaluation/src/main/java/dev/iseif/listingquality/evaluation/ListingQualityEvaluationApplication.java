package dev.iseif.listingquality.evaluation;

import dev.iseif.listingquality.evaluation.command.EvaluationCommandDispatcher;
import dev.iseif.listingquality.evaluation.command.EvaluationCommandParser;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class ListingQualityEvaluationApplication {

  public static void main(String[] args) {
    int exitCode = run(args);
    if (exitCode != 0) {
      System.exit(exitCode);
    }
  }

  static int run(String[] args) {
    try {
      if (args.length == 1 && "help".equals(args[0])) {
        System.out.println(help());
        return 0;
      }
      try (ConfigurableApplicationContext context = SpringApplication.run(
          ListingQualityEvaluationApplication.class, new String[0])) {
        EvaluationCommandParser parser = context.getBean(EvaluationCommandParser.class);
        EvaluationCommandDispatcher dispatcher = context.getBean(
            EvaluationCommandDispatcher.class);
        dispatcher.dispatch(parser.parse(args));
      }
      return 0;
    } catch (IllegalArgumentException | IllegalStateException exception) {
      System.err.println("Evaluation failed: " + exception.getMessage());
      return 2;
    }
  }

  static String help() {
    return """
        Seller Listing Quality model evaluation

        Usage:
          collect --experiment=<id> --candidate=<id> --provider=<name> --model=<id>
                  --runtime=<name> --runtime-version=<version> [options]
          compare --experiment=<id> [options]

        collect options:
          --temperature=<decimal>       default: 0.2
          --token-limit=<integer>       default: 800
          --base-url=<http-url>         default: http://localhost:8080
          --repetitions=<integer>       default: 3
          --timeout=<ISO-8601-duration> default: PT2M
          --output-root=<path>          default: target/evaluation-runs
          --overwrite=<true|false>      default: false

        compare options:
          --input-root=<path>           default: target/evaluation-runs
          --output-directory=<path>     default: <input>/<experiment>/comparison
          --semantic=<true|false>       default: false
          --resume-semantic=<true|false> reuse evaluated rows from comparison.json

        Fixed candidate workflow:
          openai-gpt-5.6-sol, gemini-3.5-flash, ollama-gemma4-e4b,
          omlx-gemma4-e4b-4bit, omlx-gemma4-12b-8bit,
          omlx-gemma4-26b-a4b-8bit, omlx-qwen3.6-27b-4bit,
          omlx-qwen3.6-35b-a3b-6bit

        Run one service profile and candidate at a time, then compare completed bundles offline.
        """;
  }
}
