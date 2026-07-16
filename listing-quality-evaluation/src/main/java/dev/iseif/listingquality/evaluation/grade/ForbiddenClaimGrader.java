package dev.iseif.listingquality.evaluation.grade;

import dev.iseif.listingquality.evaluation.result.SampleResult;
import dev.iseif.listingquality.evaluation.result.SampleStatus;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ForbiddenClaimGrader {

  private static final int CONTEXT_CHARACTERS = 80;
  private static final List<String> REJECTION_MARKERS = List.of(
      "do not", "don't", "must not", "should not", "cannot", "can't",
      " not ", " no ", "without ", "unsupported", "unverified", "misleading",
      "remove ", "avoid ", "unknown", "fails to", "failed to", "lack of", "missing",
      "verify ", "verification", "confirm ", "proving ", "before ", "assuming ");

  public List<String> matches(SampleResult sample, List<String> forbiddenClaims) {
    if (sample.status() != SampleStatus.SUCCEEDED || sample.review() == null) {
      return List.of();
    }
    List<String> responseText = new ArrayList<>();
    responseText.addAll(sample.review().missingFields());
    responseText.addAll(sample.review().issues());
    responseText.addAll(sample.review().suggestions());
    return forbiddenClaims.stream()
        .filter(claim -> responseText.stream().anyMatch(text -> isAsserted(text, claim)))
        .toList();
  }

  private boolean isAsserted(String text, String claim) {
    String normalizedText = normalize(text);
    String normalizedClaim = normalize(claim);
    int from = 0;
    while (from < normalizedText.length()) {
      int match = normalizedText.indexOf(normalizedClaim, from);
      if (match < 0) {
        return false;
      }
      int end = match + normalizedClaim.length();
      if (!insideQuotation(normalizedText, match, end)
          && !hasRejectionContext(normalizedText, match, end)) {
        return true;
      }
      from = end;
    }
    return false;
  }

  private boolean insideQuotation(String text, int start, int end) {
    return insideSymmetricQuotation(text, start, '\'')
        || insideSymmetricQuotation(text, start, '"')
        || insidePairedQuotation(text, start, end, '‘', '’')
        || insidePairedQuotation(text, start, end, '“', '”');
  }

  private boolean insideSymmetricQuotation(String text, int start, char quote) {
    int quoteCount = 0;
    for (int index = 0; index < start; index++) {
      if (text.charAt(index) == quote) {
        quoteCount++;
      }
    }
    return quoteCount % 2 == 1;
  }

  private boolean insidePairedQuotation(
      String text, int start, int end, char opening, char closing) {
    int openingIndex = text.lastIndexOf(opening, start);
    int closingBefore = text.lastIndexOf(closing, start);
    return openingIndex > closingBefore && text.indexOf(closing, end) >= 0;
  }

  private boolean hasRejectionContext(String text, int start, int end) {
    String context = text.substring(
        Math.max(0, start - CONTEXT_CHARACTERS),
        Math.min(text.length(), end + CONTEXT_CHARACTERS));
    return REJECTION_MARKERS.stream().anyMatch(context::contains);
  }

  private String normalize(String value) {
    return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
  }
}
