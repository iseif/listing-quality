package dev.iseif.listingquality.enrichment.service.book;

import dev.iseif.listingquality.enrichment.catalog.book.BookCatalogRecord;
import dev.iseif.listingquality.enrichment.catalog.book.Isbn;
import dev.iseif.listingquality.enrichment.model.book.BookEnrichmentRequest;
import dev.iseif.listingquality.enrichment.model.book.BookField;
import dev.iseif.listingquality.enrichment.model.book.GeneratedBookValue;
import dev.iseif.listingquality.enrichment.service.book.exception.BookEnrichmentValidationFailure;
import dev.iseif.listingquality.enrichment.service.book.exception.InvalidBookEnrichmentResponseException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.StringNode;

import java.util.Locale;
import java.util.Map;

/**
 * Reads seller and catalog values as JSON, converts the model transport value into its natural
 * JSON type, and decides when two values mean the same thing. It holds no enrichment policy.
 */
@Component
final class BookValueMapper {

  private static final String ATTRIBUTE_PREFIX = "attributes.";

  private final ObjectMapper objectMapper;

  BookValueMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * Converts the model-facing transport value into the natural JSON type used by the public API,
   * rejecting a value whose populated slot disagrees with its declared kind.
   */
  public JsonNode fromGenerated(GeneratedBookValue generated, Enum<?> field) {
    if (generated == null || generated.kind() == null || generated.text() == null
        || generated.items() == null || generated.integer() == null) {
      throw invalid(field);
    }
    return switch (generated.kind()) {
      case TEXT -> {
        requireEmptySlots(field, generated.items().isEmpty(), generated.integer() == 0);
        yield text(generated.text());
      }
      case ITEMS -> {
        requireEmptySlots(field, generated.text().isEmpty(), generated.integer() == 0);
        yield json(generated.items());
      }
      case INTEGER -> {
        requireEmptySlots(field, generated.text().isEmpty(), generated.items().isEmpty());
        yield json(generated.integer());
      }
    };
  }

  public JsonNode catalogValue(BookCatalogRecord catalogRecord, BookField field) {
    return switch (field) {
      case TITLE -> text(catalogRecord.title());
      case SUBTITLE -> text(catalogRecord.subtitle());
      case AUTHORS -> json(catalogRecord.authors());
      case PUBLISHER -> text(catalogRecord.publisher());
      case PUBLISHED_DATE -> text(catalogRecord.publishedDate());
      case DESCRIPTION -> text(catalogRecord.description());
      case ISBN10 -> text(catalogRecord.isbn10());
      case ISBN13 -> text(catalogRecord.isbn13());
      case LANGUAGE -> text(catalogRecord.language());
      case PAGE_COUNT -> json(catalogRecord.pageCount());
      case CATEGORIES -> json(catalogRecord.categories());
    };
  }

  /**
   * The seller value already present for a field, or null when the seller left it empty.
   */
  public JsonNode sellerValue(BookEnrichmentRequest request, BookField field) {
    return switch (field) {
      case TITLE -> text(request.title());
      case DESCRIPTION -> textIfPresent(request.description());
      default -> attributeValue(request, field.externalName());
    };
  }

  /**
   * The seller value behind an evidence citation such as {@code title} or
   * {@code attributes.publisher}, or null when the citation points at nothing usable.
   */
  public JsonNode sellerSourceValue(BookEnrichmentRequest request, String sourceField) {
    if (BookField.TITLE.externalName().equals(sourceField)) {
      return text(request.title());
    }
    if (BookField.DESCRIPTION.externalName().equals(sourceField)) {
      return textIfPresent(request.description());
    }
    if (sourceField.startsWith(ATTRIBUTE_PREFIX)) {
      return attributeValue(request, sourceField.substring(ATTRIBUTE_PREFIX.length()));
    }
    return null;
  }

  public boolean equivalent(BookField field, JsonNode left, JsonNode right) {
    if (left == null || right == null) {
      return false;
    }
    if (isIsbn(field)) {
      return left.isString() && right.isString()
          && Isbn.normalize(left.asString()).equals(Isbn.normalize(right.asString()));
    }
    if (left.isString() && right.isString()) {
      return normalizeText(left.asString()).equals(normalizeText(right.asString()));
    }
    return left.equals(right);
  }

  public boolean isValidIsbn(BookField field, JsonNode value) {
    return !isIsbn(field)
        || value != null && value.isString() && Isbn.normalize(value.asString()).isPresent();
  }

  private boolean isIsbn(BookField field) {
    return field == BookField.ISBN10 || field == BookField.ISBN13;
  }

  private JsonNode attributeValue(BookEnrichmentRequest request, String attributeName) {
    return request.attributes().entrySet().stream()
        .filter(entry -> normalizeKey(entry.getKey()).equals(normalizeKey(attributeName)))
        .map(Map.Entry::getValue)
        .map(this::text)
        .findFirst()
        .orElse(null);
  }

  private void requireEmptySlots(Enum<?> field, boolean... emptySlots) {
    for (boolean empty : emptySlots) {
      if (!empty) {
        throw invalid(field);
      }
    }
  }

  private String normalizeKey(String value) {
    return value == null ? "" : value.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
  }

  private String normalizeText(String value) {
    return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
  }

  private JsonNode text(String value) {
    return value == null ? null : StringNode.valueOf(value);
  }

  private JsonNode textIfPresent(String value) {
    return value == null || value.isBlank() ? null : text(value);
  }

  private JsonNode json(Object value) {
    return value == null ? null : objectMapper.valueToTree(value);
  }

  private InvalidBookEnrichmentResponseException invalid(Enum<?> field) {
    return new InvalidBookEnrichmentResponseException(
        BookEnrichmentValidationFailure.GENERATED_VALUE_INVALID, field);
  }
}
