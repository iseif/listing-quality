package dev.iseif.listingquality.enrichment.catalog.book.google;

import dev.iseif.listingquality.enrichment.catalog.book.BookCatalogClient;
import dev.iseif.listingquality.enrichment.catalog.book.BookCatalogRecord;
import dev.iseif.listingquality.enrichment.catalog.book.BookCatalogUnavailableException;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class GoogleBooksCatalogClient implements BookCatalogClient {

  private static final Logger log = LoggerFactory.getLogger(GoogleBooksCatalogClient.class);
  private static final String ISBN_10 = "ISBN_10";
  private static final String ISBN_13 = "ISBN_13";
  private static final Set<Integer> RETRYABLE_STATUS_CODES =
      Set.of(408, 429, 500, 502, 503, 504);

  private final RestClient restClient;
  private final ObjectMapper objectMapper;
  private final GoogleBooksProperties properties;
  private final Retry retry;

  public GoogleBooksCatalogClient(
      RestClient.Builder restClientBuilder,
      ObjectMapper objectMapper,
      GoogleBooksProperties properties) {
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
    requestFactory.setReadTimeout(properties.timeout());
    this.restClient = restClientBuilder
        .baseUrl(properties.baseUrl().toString())
        .requestFactory(requestFactory)
        .observationConvention(new GoogleBooksClientRequestObservationConvention())
        .build();
    this.objectMapper = objectMapper;
    this.properties = properties;
    IntervalFunction intervalFunction = IntervalFunction.ofExponentialRandomBackoff(
        properties.initialBackoff(), 2.0, 0.5, properties.maxBackoff());
    this.retry = Retry.of("google-books", RetryConfig.custom()
        .maxAttempts(properties.maxAttempts())
        .intervalFunction(intervalFunction)
        .retryOnException(this::isRetryable)
        .build());
    this.retry.getEventPublisher().onRetry(event -> log.warn(
        "Retrying Google Books lookup: category={}, retryAttempt={}, waitMs={}",
        failureCategory(event.getLastThrowable()),
        event.getNumberOfRetryAttempts(),
        event.getWaitInterval().toMillis()));
  }

  @Override
  public Optional<BookCatalogRecord> findByIsbn(String normalizedIsbn) {
    return fetch("isbn:" + normalizedIsbn, 1).stream().findFirst();
  }

  @Override
  public List<BookCatalogRecord> search(String title, String author, int limit) {
    int boundedLimit = Math.clamp(limit, 1, properties.maxResults());
    String query = "intitle:" + title + (author == null || author.isBlank()
        ? ""
        : " inauthor:" + author);
    return fetch(query, boundedLimit);
  }

  private List<BookCatalogRecord> fetch(String query, int maxResults) {
    try {
      byte[] response = retry.executeSupplier(() -> executeRequest(query, maxResults));
      GoogleBooksResponse books = objectMapper.readValue(response, GoogleBooksResponse.class);
      if (books == null || books.items() == null) {
        return List.of();
      }
      return books.items().stream()
          .filter(Objects::nonNull)
          .map(this::map)
          .toList();
    } catch (BookCatalogUnavailableException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      log.warn(
          "Google Books lookup failed: category={}, retryable={}",
          failureCategory(exception),
          isRetryable(exception));
      throw new BookCatalogUnavailableException(exception);
    }
  }

  private byte[] executeRequest(String query, int maxResults) {
    return restClient.get()
        .uri(uriBuilder -> uriBuilder
            .path("/volumes")
            .queryParam("q", query)
            .queryParam("maxResults", maxResults)
            .queryParam("key", properties.apiKey())
            .build())
        .exchange((request, httpResponse) -> {
          if (!httpResponse.getStatusCode().is2xxSuccessful()) {
            throw new GoogleBooksRequestException(httpResponse.getStatusCode().value());
          }
          byte[] body = httpResponse.getBody().readNBytes(properties.maxResponseBytes() + 1);
          if (body.length > properties.maxResponseBytes()) {
            throw new BookCatalogUnavailableException();
          }
          return body;
        });
  }

  private boolean isRetryable(Throwable failure) {
    if (failure instanceof GoogleBooksRequestException requestFailure) {
      return RETRYABLE_STATUS_CODES.contains(requestFailure.statusCode());
    }
    return failure instanceof ResourceAccessException;
  }

  private String failureCategory(Throwable failure) {
    if (failure instanceof GoogleBooksRequestException requestFailure) {
      return "http-" + requestFailure.statusCode();
    }
    if (failure instanceof ResourceAccessException) {
      return "transport";
    }
    return "invalid-response";
  }

  private BookCatalogRecord map(GoogleBooksResponse.Volume volume) {
    GoogleBooksResponse.VolumeInfo info = volume.volumeInfo();
    if (info == null) {
      throw new BookCatalogUnavailableException();
    }
    return new BookCatalogRecord(
        volume.id(),
        sourceUrl(volume),
        info.title(),
        info.subtitle(),
        info.authors(),
        info.publisher(),
        info.publishedDate(),
        info.description(),
        identifier(info, ISBN_10),
        identifier(info, ISBN_13),
        info.language(),
        info.pageCount(),
        info.categories());
  }

  private URI sourceUrl(GoogleBooksResponse.Volume volume) {
    try {
      return URI.create(volume.selfLink());
    } catch (RuntimeException _) {
      return URI.create("https://books.google.com/books?id=" + volume.id());
    }
  }

  private String identifier(GoogleBooksResponse.VolumeInfo info, String type) {
    if (info.industryIdentifiers() == null) {
      return null;
    }
    return info.industryIdentifiers().stream()
        .filter(identifier -> type.equals(identifier.type()))
        .map(GoogleBooksResponse.IndustryIdentifier::identifier)
        .findFirst()
        .orElse(null);
  }
}
