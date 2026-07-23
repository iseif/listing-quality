package dev.iseif.listingquality.enrichment.catalog.book.google;

import io.micrometer.common.KeyValue;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.observation.ClientRequestObservationContext;
import org.springframework.mock.http.client.MockClientHttpRequest;

import java.net.URI;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleBooksClientRequestObservationConventionTest {

  @Test
  void removesQueryDataFromTheObservedRequestUrl() {
    URI requestUri = URI.create(
        "https://www.googleapis.com/books/v1/volumes"
            + "?q=intitle%3AEffective%20Java&maxResults=3&key=secret-key");
    var request = new MockClientHttpRequest(HttpMethod.GET, requestUri);
    var context = new ClientRequestObservationContext(request);

    var convention = new GoogleBooksClientRequestObservationConvention();

    String observedUrl = StreamSupport.stream(
            convention.getHighCardinalityKeyValues(context).spliterator(), false)
        .filter(keyValue -> "http.url".equals(keyValue.getKey()))
        .map(KeyValue::getValue)
        .findFirst()
        .orElseThrow();

    assertThat(observedUrl)
        .isEqualTo("https://www.googleapis.com/books/v1/volumes")
        .doesNotContain("Effective", "secret-key", "?", "q=");
  }
}
