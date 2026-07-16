package dev.iseif.listingquality.evaluation.report;

import dev.iseif.listingquality.evaluation.compare.CandidateScorecard;
import dev.iseif.listingquality.evaluation.compare.ScorecardReport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class MarkdownReportWriter {

  public Path write(ScorecardReport report, Path outputDirectory) {
    try {
      Files.createDirectories(outputDirectory);
      Path output = outputDirectory.resolve("comparison.md");
      Files.writeString(output, render(report), StandardCharsets.UTF_8);
      return output;
    } catch (IOException exception) {
      throw new IllegalStateException("Markdown comparison report could not be written", exception);
    }
  }

  private String render(ScorecardReport report) {
    StringBuilder markdown = new StringBuilder();
    markdown.append("# Listing Quality Model Comparison\n\n")
        .append("Experiment: `").append(report.experiment()).append("`\n\n")
        .append("Dataset: `").append(report.datasetVersion()).append("`\n\n")
        .append("Semantic rubric: `").append(report.semanticRubricVersion()).append("`\n\n")
        .append("Routing policy: `").append(report.routingPolicyVersion()).append("`\n\n")
        .append("## How to read this scorecard\n\n")
        .append("Safety and semantic quality answer different questions. Operations intentionally has no global pass state because acceptable reviewer load, latency, and cost depend on the product.\n\n")
        .append("## Safety\n\n")
        .append("| Candidate | Valid responses | Required-review recall | False negatives | Forbidden claims | Status |\n")
        .append("| --- | ---: | ---: | ---: | ---: | --- |\n");
    for (CandidateScorecard scorecard : report.candidates()) {
      markdown.append("| ").append(scorecard.candidate())
          .append(" | ").append(percent(scorecard.safety().validResponseRate()))
          .append(" | ").append(percent(scorecard.safety().requiredReviewRecall()))
          .append(" | ").append(scorecard.safety().falseNegativeCount())
          .append(" | ").append(scorecard.safety().forbiddenClaimCount())
          .append(" | ").append(scorecard.safety().safetyStatus())
          .append(" |\n");
    }

    markdown.append("\n## Semantic quality\n\n")
        .append("Expected-concept coverage and judge coverage reuse the v1 semantic evidence.\n\n")
        .append("| Candidate | Expected concepts (v1) | Semantic judge coverage | Status |\n")
        .append("| --- | ---: | ---: | --- |\n");
    for (CandidateScorecard scorecard : report.candidates()) {
      markdown.append("| ").append(scorecard.candidate())
          .append(" | ").append(conceptCoverage(
              scorecard.quality().expectedConceptCoverage()))
          .append(" | ").append(percent(scorecard.quality().semanticJudgeCoverage()))
          .append(" | ").append(scorecard.quality().qualityStatus())
          .append(" |\n");
    }

    markdown.append("\n## Operations\n\n")
        .append("| Candidate | TP | FN | FP | TN | Precision | Recall | False-positive rate | Accuracy | Disagreement cases | Disagreement rate | Median ms | P95 ms | Cost USD |\n")
        .append("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n");
    for (CandidateScorecard scorecard : report.candidates()) {
      var operations = scorecard.operations();
      markdown.append("| ").append(scorecard.candidate())
          .append(" | ").append(operations.truePositiveCount())
          .append(" | ").append(operations.falseNegativeCount())
          .append(" | ").append(operations.falsePositiveCount())
          .append(" | ").append(operations.trueNegativeCount())
          .append(" | ").append(percent(operations.humanReviewPrecision()))
          .append(" | ").append(percent(operations.humanReviewRecall()))
          .append(" | ").append(percent(operations.falsePositiveRate()))
          .append(" | ").append(percent(operations.routingAccuracy()))
          .append(" | ").append(operations.disagreementCaseCount())
          .append(" | ").append(percent(operations.disagreementCaseRate()))
          .append(" | ").append(operations.medianLatencyMs())
          .append(" | ").append(operations.p95LatencyMs())
          .append(" | ").append(cost(operations.cost()))
          .append(" |\n");
    }

    markdown.append("\nCost is an estimate when token metrics and a dated cloud price are available. ")
        .append("Local cost is not applicable here and excludes hardware, electricity, engineering, and hosting.\n\n")
        .append("## Limitations\n\n");
    for (String limitation : report.limitations()) {
      markdown.append("- ").append(limitation).append("\n");
    }
    return markdown.toString();
  }

  private String percent(double value) {
    return "%.1f%%".formatted(value * 100.0);
  }

  private String percent(Double value) {
    return value == null ? "not applicable" : "%.1f%%".formatted(value * 100.0);
  }

  private String conceptCoverage(Double value) {
    return value == null ? "not reported" : "%.1f%%".formatted(value);
  }

  private String cost(CostEstimate estimate) {
    return estimate.status() == CostStatus.ESTIMATED
        ? estimate.usd().toPlainString()
        : estimate.status().name().toLowerCase().replace('_', ' ');
  }
}
