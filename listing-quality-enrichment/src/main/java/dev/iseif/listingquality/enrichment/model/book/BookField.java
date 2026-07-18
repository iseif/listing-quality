package dev.iseif.listingquality.enrichment.model.book;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum BookField {
  TITLE("title"),
  SUBTITLE("subtitle"),
  AUTHORS("authors"),
  PUBLISHER("publisher"),
  PUBLISHED_DATE("publishedDate"),
  DESCRIPTION("description"),
  ISBN10("isbn10"),
  ISBN13("isbn13"),
  LANGUAGE("language"),
  PAGE_COUNT("pageCount"),
  CATEGORIES("categories");

  private static final Map<String, BookField> BY_EXTERNAL_NAME = Stream.of(values())
      .collect(Collectors.toUnmodifiableMap(BookField::externalName, Function.identity()));

  private final String externalName;

  BookField(String externalName) {
    this.externalName = externalName;
  }

  /**
   * The name this field carries in seller attributes, catalog records, and evidence citations.
   */
  public String externalName() {
    return externalName;
  }

  public static Optional<BookField> byExternalName(String externalName) {
    return Optional.ofNullable(BY_EXTERNAL_NAME.get(externalName));
  }
}
