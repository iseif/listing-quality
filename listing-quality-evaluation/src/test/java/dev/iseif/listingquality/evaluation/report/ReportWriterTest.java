package dev.iseif.listingquality.evaluation.report;

import dev.iseif.listingquality.evaluation.compare.*;
import dev.iseif.listingquality.evaluation.result.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReportWriterTest {

  @TempDir
  Path temporaryDirectory;

  @Test
  void writesStableJsonAndAuditableMarkdownWithoutLocalPaths() throws Exception {
    CandidateScorecard scorecard = new CandidateScorecard(
        "openai-gpt-5.6", "openai", "gpt-5.6", BigDecimal.ONE, 36,
        SafetySummary.from(1.0, 1.0, 0, 0),
        QualitySummary.from(85.0, 1.0),
        new OperationalSummary(
            24, 0, 0, 12, null, 1.0, 0.0, 1.0, 0, 0.0,
            1200, 1900, new TokenUsage(1000L, 500L, 1500L),
            new CostEstimate(CostStatus.ESTIMATED, new BigDecimal("0.020000"))));
    ScorecardReport report = new ScorecardReport(
        2, "listing-quality-v1", "listing-quality-v1", "a".repeat(64),
        "listing-quality-rubric-v1", "listing-quality-routing-v2", "b".repeat(64),
        List.of(scorecard), List.of(),
        List.of("The dataset is intentionally small and task specific."));

    Path json = new JsonReportWriter(new ObjectMapper()).write(report, temporaryDirectory);
    Path markdown = new MarkdownReportWriter().write(report, temporaryDirectory);

    assertThat(json).hasFileName("comparison.json");
    assertThat(markdown).hasFileName("comparison.md");
    assertThat(Files.readString(json))
        .contains("\"schemaVersion\" : 2", "openai-gpt-5.6", "\"temperature\" : 1",
            "0.020000", "listing-quality-rubric-v1",
            "\"routingPolicyVersion\" : \"listing-quality-routing-v2\"",
            "\"safety\"", "\"quality\"", "\"operations\"",
            "\"humanReviewPrecision\" : null")
        .doesNotContain("eligibility")
        .doesNotContain("/Users/");
    assertThat(Files.readString(markdown))
        .contains("# Listing Quality Model Comparison", "How to read this scorecard",
            "## Safety", "## Semantic quality", "## Operations", "## Limitations",
            "not applicable", "openai-gpt-5.6")
        .doesNotContain("Eligibility", "Product threshold")
        .doesNotContain("/Users/");
  }

  @Test
  void priceTableUsesDatedOfficialSources() {
    PriceTable prices = PriceTable.load(
        new ObjectMapper(), "pricing/model-prices-2026-07-15.json");

    assertThat(prices.find("gpt-5.6")).get()
        .satisfies(price -> {
          assertThat(price.inputUsdPerMillion()).isEqualByComparingTo("5.00");
          assertThat(price.outputUsdPerMillion()).isEqualByComparingTo("30.00");
          assertThat(price.verifiedAt()).isEqualTo(LocalDate.of(2026, 7, 15));
          assertThat(price.sourceUrl()).contains("openai.com");
        });
    assertThat(prices.find("gpt-5.6-sol")).get()
        .satisfies(price -> {
          assertThat(price.inputUsdPerMillion()).isEqualByComparingTo("5.00");
          assertThat(price.outputUsdPerMillion()).isEqualByComparingTo("30.00");
          assertThat(price.verifiedAt()).isEqualTo(LocalDate.of(2026, 7, 15));
          assertThat(price.sourceUrl()).contains("openai.com");
        });
    assertThat(prices.find("gemini-3.5-flash")).get()
        .satisfies(price -> {
          assertThat(price.inputUsdPerMillion()).isEqualByComparingTo("1.50");
          assertThat(price.outputUsdPerMillion()).isEqualByComparingTo("9.00");
          assertThat(price.sourceUrl()).contains("ai.google.dev");
        });
  }
}
