package dev.iseif.listingquality.enrichment.model.shoe;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

public record ShoeColorEnrichmentRequest(
    @NotBlank @Size(max = 100) String listingId,
    @NotNull @Size(max = 19) List<@NotNull ShoeColor> listingColors,
    @NotNull @Size(min = 1, max = 3) List<@NotNull URI> imageUrls) {

  public ShoeColorEnrichmentRequest {
    listingColors = listingColors == null ? List.of() : List.copyOf(listingColors);
    imageUrls = imageUrls == null ? List.of() : List.copyOf(imageUrls);
  }

  @AssertTrue(message = "listing colors and normalized image URLs must be distinct")
  public boolean hasDistinctValues() {
    return new HashSet<>(listingColors).size() == listingColors.size()
        && imageUrls.stream().map(ShoeColorEnrichmentRequest::normalizedUrl).distinct().count()
        == imageUrls.size();
  }

  private static String normalizedUrl(URI uri) {
    if (uri == null) {
      return "";
    }
    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
    int port = uri.getPort();
    String authority = port == -1 ? host : host + ":" + port;
    return scheme + "://" + authority + uri.normalize().getRawPath()
        + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
  }
}
