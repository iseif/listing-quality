package dev.iseif.listingquality.enrichment.catalog.book.google;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.iseif.listingquality.enrichment.catalog.book.BookCatalogUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleBooksCatalogClientTest {

  private static final String CLEAN_CODE_RESPONSE = """
      {
        "items": [{
          "id": "zyTCAlFPjgYC",
          "selfLink": "https://www.googleapis.com/books/v1/volumes/zyTCAlFPjgYC",
          "volumeInfo": {
            "title": "Clean Code",
            "subtitle": "A Handbook of Agile Software Craftsmanship",
            "authors": ["Robert C. Martin"],
            "publisher": "Prentice Hall",
            "publishedDate": "2008-08-01",
            "description": "A handbook about writing maintainable software.",
            "industryIdentifiers": [
              {"type": "ISBN_10", "identifier": "0132350882"},
              {"type": "ISBN_13", "identifier": "9780132350884"}
            ],
            "pageCount": 464,
            "categories": ["Computers"],
            "language": "en"
          }
        }]
      }
      """;

  private HttpServer server;
  private final AtomicReference<String> query = new AtomicReference<>();

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.start();
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  @Test
  void findsAndMapsABookByNormalizedIsbn() {
    server.createContext("/books/v1/volumes", exchange -> respond(exchange, 200, CLEAN_CODE_RESPONSE));
    GoogleBooksCatalogClient client = client(1024 * 128);

    var result = client.findByIsbn("9780132350884");

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().recordId()).isEqualTo("zyTCAlFPjgYC");
    assertThat(result.orElseThrow().publisher()).isEqualTo("Prentice Hall");
    assertThat(result.orElseThrow().isbn10()).isEqualTo("0132350882");
    assertThat(result.orElseThrow().isbn13()).isEqualTo("9780132350884");
    assertThat(result.orElseThrow().description())
        .isEqualTo("A handbook about writing maintainable software.");
    assertThat(decodedQuery()).contains("q=isbn:9780132350884", "maxResults=1", "key=test-key");
  }

  @Test
  void boundsTitleAndAuthorSearchToThreeResults() {
    server.createContext("/books/v1/volumes", exchange -> respond(exchange, 200, CLEAN_CODE_RESPONSE));
    GoogleBooksCatalogClient client = client(1024 * 128);

    assertThat(client.search("Clean Code", "Robert Martin", 20)).hasSize(1);
    assertThat(decodedQuery())
        .contains("q=intitle:Clean Code inauthor:Robert Martin", "maxResults=3");
  }

  @Test
  void translatesHttpFailuresWithoutLeakingTheResponseBody() {
    server.createContext("/books/v1/volumes", exchange ->
        respond(exchange, 500, "secret upstream diagnostic"));
    GoogleBooksCatalogClient client = client(1024 * 128);

    assertThatThrownBy(() -> client.findByIsbn("9780132350884"))
        .isInstanceOf(BookCatalogUnavailableException.class)
        .hasMessage("Google Books catalog is unavailable")
        .hasMessageNotContaining("secret upstream diagnostic");
  }

  @Test
  void rejectsResponsesAboveTheConfiguredLimit() {
    server.createContext("/books/v1/volumes", exchange ->
        respond(exchange, 200, CLEAN_CODE_RESPONSE + " ".repeat(1024)));
    GoogleBooksCatalogClient client = client(256);

    assertThatThrownBy(() -> client.findByIsbn("9780132350884"))
        .isInstanceOf(BookCatalogUnavailableException.class)
        .hasMessage("Google Books catalog is unavailable");
  }

  @Test
  void retriesTransientServerFailuresUntilTheLookupSucceeds() {
    AtomicInteger attempts = new AtomicInteger();
    server.createContext("/books/v1/volumes", exchange -> {
      if (attempts.incrementAndGet() < 3) {
        respond(exchange, 503, "temporary upstream failure");
      } else {
        respond(exchange, 200, CLEAN_CODE_RESPONSE);
      }
    });
    GoogleBooksCatalogClient client = client(1024 * 128);

    var result = client.findByIsbn("9780132350884");

    assertThat(result).isPresent();
    assertThat(attempts).hasValue(3);
  }

  @Test
  void stopsRetryingAfterTheConfiguredNumberOfAttempts() {
    AtomicInteger attempts = new AtomicInteger();
    server.createContext("/books/v1/volumes", exchange -> {
      attempts.incrementAndGet();
      respond(exchange, 503, "temporary upstream failure");
    });
    GoogleBooksCatalogClient client = client(1024 * 128);

    assertThatThrownBy(() -> client.findByIsbn("9780132350884"))
        .isInstanceOf(BookCatalogUnavailableException.class)
        .hasMessage("Google Books catalog is unavailable");
    assertThat(attempts).hasValue(3);
  }

  @Test
  void doesNotRetryPermanentClientFailures() {
    AtomicInteger attempts = new AtomicInteger();
    server.createContext("/books/v1/volumes", exchange -> {
      attempts.incrementAndGet();
      respond(exchange, 403, "secret credential diagnostic");
    });
    GoogleBooksCatalogClient client = client(1024 * 128);

    assertThatThrownBy(() -> client.findByIsbn("9780132350884"))
        .isInstanceOf(BookCatalogUnavailableException.class)
        .hasMessage("Google Books catalog is unavailable")
        .hasMessageNotContaining("secret credential diagnostic");
    assertThat(attempts).hasValue(1);
  }

  private GoogleBooksCatalogClient client(int maxResponseBytes) {
    GoogleBooksProperties properties = new GoogleBooksProperties(
        URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/books/v1"),
        "test-key",
        Duration.ofSeconds(2),
        3,
        maxResponseBytes,
        3,
        Duration.ofMillis(1),
        Duration.ofMillis(2));
    return new GoogleBooksCatalogClient(RestClient.builder(), JsonMapper.shared(), properties);
  }

  private void respond(HttpExchange exchange, int status, String body) throws IOException {
    query.set(exchange.getRequestURI().getRawQuery());
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }

  private String decodedQuery() {
    return URLDecoder.decode(query.get(), StandardCharsets.UTF_8);
  }
}
