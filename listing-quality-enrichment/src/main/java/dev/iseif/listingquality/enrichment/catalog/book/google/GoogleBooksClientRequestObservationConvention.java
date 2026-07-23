package dev.iseif.listingquality.enrichment.catalog.book.google;

import io.micrometer.common.KeyValue;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.observation.ClientRequestObservationContext;
import org.springframework.http.client.observation.DefaultClientRequestObservationConvention;

import java.net.URI;

final class GoogleBooksClientRequestObservationConvention
    extends DefaultClientRequestObservationConvention {

  @Override
  protected KeyValue requestUri(ClientRequestObservationContext context) {
    HttpRequest carrier = context.getCarrier();
    if (carrier == null) {
      return KeyValue.of("http.url", "none");
    }
    URI requestUri = carrier.getURI();
    String path = requestUri.getRawPath();
    String sanitizedUrl = requestUri.getScheme()
        + "://"
        + requestUri.getRawAuthority()
        + (path == null || path.isBlank() ? "/" : path);
    return KeyValue.of("http.url", sanitizedUrl);
  }
}
