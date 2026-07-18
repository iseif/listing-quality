package dev.iseif.listingquality.enrichment.service.book;

import dev.iseif.listingquality.enrichment.model.book.BookDiscoveryField;
import dev.iseif.listingquality.enrichment.model.book.DerivedBookAttribute;
import dev.iseif.listingquality.enrichment.service.book.exception.BookEnrichmentValidationFailure;
import dev.iseif.listingquality.enrichment.service.book.exception.InvalidBookEnrichmentResponseException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.*;

/**
 * Bounds the discovery metadata the model is allowed to infer.
 *
 * <p>These values are interpretations, so they are not checked against their evidence. They are
 * checked against a fixed vocabulary, a size limit, and a numeric range instead, which is what
 * keeps an inference from turning into an unbounded free-text claim.
 */
@Component
final class DerivedBookValueValidator {

  private static final Set<String> ALLOWED_GENRES = Set.of(
      "ACTION", "ADVENTURE", "CLASSICS", "CONTEMPORARY", "DRAMA", "DYSTOPIAN",
      "FANTASY", "HISTORICAL_FICTION", "HORROR", "HUMOR", "MAGICAL_REALISM",
      "MYSTERY", "POETRY", "ROMANCE", "SCIENCE_FICTION", "SHORT_STORIES",
      "THRILLER", "ART_AND_PHOTOGRAPHY", "BIOGRAPHY", "BUSINESS", "COOKING",
      "FINANCE", "FOOD", "HISTORY", "HOBBIES_AND_CRAFTS", "MEMOIR", "NATURE",
      "PHILOSOPHY", "POLITICS", "RELIGION_AND_SPIRITUALITY", "SCIENCE", "SELF_HELP",
      "SPORTS", "TECHNOLOGY", "TRAVEL", "TRUE_CRIME", "WELLNESS", "DICTIONARY",
      "EDUCATION", "ENCYCLOPEDIA", "LANGUAGE_LEARNING", "MAPS", "REFERENCE",
      "STUDY_GUIDES", "TEXTBOOK", "THESAURUS", "BOARD_BOOKS", "CHAPTER_BOOKS",
      "COMICS", "FAIRYTALES", "GRAPHIC_NOVELS", "MIDDLE_GRADE", "PICTURE_BOOKS",
      "YOUNG_ADULT");
  private static final Set<String> ALLOWED_AUDIENCES =
      Set.of("CHILDREN", "YOUNG_ADULT", "ADULT");
  private static final Set<String> ALLOWED_PACING = Set.of("SLOW", "MEDIUM", "FAST");
  private static final Set<String> ALLOWED_DIFFICULTY = Set.of("EASY", "MEDIUM", "ADVANCED");
  private static final Set<String> ALLOWED_LENGTHS = Set.of("SHORT", "MEDIUM", "LONG");

  private static final int MIN_LIST_SIZE = 1;
  private static final int MAX_LIST_SIZE = 6;
  private static final int MAX_LIST_ITEM_LENGTH = 60;
  private static final int MAX_SUMMARY_LENGTH = 300;
  private static final int MIN_AGE = 0;
  private static final int MAX_AGE = 120;

  public void validateValue(BookDiscoveryField field, JsonNode value) {
    switch (field) {
      case GENRES -> validateStringList(field, value, ALLOWED_GENRES);
      case TONE, THEMES, KEYWORDS -> validateStringList(field, value, null);
      case TARGET_AUDIENCE -> validateVocabulary(field, value, ALLOWED_AUDIENCES);
      case PACING -> validateVocabulary(field, value, ALLOWED_PACING);
      case READING_DIFFICULTY -> validateVocabulary(field, value, ALLOWED_DIFFICULTY);
      case BOOK_LENGTH_ESTIMATE -> validateVocabulary(field, value, ALLOWED_LENGTHS);
      case MIN_AGE, MAX_AGE -> validateAge(field, value);
      case SHORT_SUMMARY -> validateSummary(field, value);
    }
  }

  /**
   * Rejects the whole response when the accepted attributes describe an impossible age range.
   * This is a cross-field contract failure, not a single unverifiable item, so it is not
   * quarantined.
   */
  public void validateAgeRange(List<DerivedBookAttribute> attributes) {
    OptionalInt minimum = ageValue(attributes, BookDiscoveryField.MIN_AGE);
    OptionalInt maximum = ageValue(attributes, BookDiscoveryField.MAX_AGE);
    if (minimum.isPresent() && maximum.isPresent() && minimum.getAsInt() > maximum.getAsInt()) {
      throw new InvalidBookEnrichmentResponseException(
          BookEnrichmentValidationFailure.AGE_RANGE_INVALID, null);
    }
  }

  private void validateStringList(
      BookDiscoveryField field, JsonNode value, Set<String> allowedValues) {
    if (value == null || !value.isArray()
        || value.size() < MIN_LIST_SIZE || value.size() > MAX_LIST_SIZE) {
      throw invalid(field);
    }
    Set<String> uniqueValues = new HashSet<>();
    for (JsonNode item : value) {
      if (!item.isString() || item.asString().isBlank()
          || item.asString().length() > MAX_LIST_ITEM_LENGTH) {
        throw invalid(field);
      }
      String normalized = normalize(item);
      if (!uniqueValues.add(normalized)
          || allowedValues != null && !allowedValues.contains(normalized)) {
        throw invalid(field);
      }
    }
  }

  private void validateVocabulary(
      BookDiscoveryField field, JsonNode value, Set<String> allowedValues) {
    if (value == null || !value.isString() || !allowedValues.contains(normalize(value))) {
      throw invalid(field);
    }
  }

  private void validateAge(BookDiscoveryField field, JsonNode value) {
    if (value == null || !value.isIntegralNumber()
        || value.asInt() < MIN_AGE || value.asInt() > MAX_AGE) {
      throw invalid(field);
    }
  }

  private void validateSummary(BookDiscoveryField field, JsonNode value) {
    if (value == null || !value.isString() || value.asString().isBlank()
        || value.asString().length() > MAX_SUMMARY_LENGTH) {
      throw invalid(field);
    }
  }

  private OptionalInt ageValue(
      List<DerivedBookAttribute> attributes, BookDiscoveryField field) {
    return attributes.stream()
        .filter(attribute -> attribute.field() == field)
        .mapToInt(attribute -> attribute.value().asInt())
        .findFirst();
  }

  private String normalize(JsonNode value) {
    return value.asString().trim().toUpperCase(Locale.ROOT);
  }

  private InvalidBookEnrichmentResponseException invalid(BookDiscoveryField field) {
    return new InvalidBookEnrichmentResponseException(
        BookEnrichmentValidationFailure.DERIVED_VALUE_INVALID, field);
  }
}
