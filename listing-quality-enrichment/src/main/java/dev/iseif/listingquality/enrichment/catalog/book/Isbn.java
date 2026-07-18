package dev.iseif.listingquality.enrichment.catalog.book;

import java.util.Locale;
import java.util.Optional;

public final class Isbn {

  private Isbn() {
  }

  public static Optional<String> normalize(String candidate) {
    if (candidate == null) {
      return Optional.empty();
    }
    String normalized = candidate.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    if (normalized.length() == 10 && validIsbn10(normalized)) {
      return Optional.of(normalized);
    }
    if (normalized.length() == 13 && validIsbn13(normalized)) {
      return Optional.of(normalized);
    }
    return Optional.empty();
  }

  private static boolean validIsbn10(String isbn) {
    if (!isbn.substring(0, 9).chars().allMatch(Character::isDigit)) {
      return false;
    }
    char checkDigit = isbn.charAt(9);
    if (!Character.isDigit(checkDigit) && checkDigit != 'X') {
      return false;
    }
    int sum = 0;
    for (int index = 0; index < 10; index++) {
      int value = index == 9 && checkDigit == 'X'
          ? 10
          : Character.digit(isbn.charAt(index), 10);
      sum += value * (10 - index);
    }
    return sum % 11 == 0;
  }

  private static boolean validIsbn13(String isbn) {
    if (!isbn.chars().allMatch(Character::isDigit)) {
      return false;
    }
    int sum = 0;
    for (int index = 0; index < 12; index++) {
      int value = Character.digit(isbn.charAt(index), 10);
      sum += value * (index % 2 == 0 ? 1 : 3);
    }
    int expectedCheckDigit = (10 - sum % 10) % 10;
    return expectedCheckDigit == Character.digit(isbn.charAt(12), 10);
  }
}
