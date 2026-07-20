package dev.iseif.listingquality.enrichment.media;

import dev.iseif.listingquality.enrichment.media.exception.ImageSourceUnavailableException;
import dev.iseif.listingquality.enrichment.media.exception.RejectedImageException;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrustedImageUriValidatorTest {

  private static final HostAddressResolver PUBLIC_RESOLVER = host ->
      List.of(InetAddress.getByName("8.8.8.8"));

  @Test
  void acceptsAnHttpsUrlFromAnAllowedPublicHost() {
    var validator = new TrustedImageUriValidator(Set.of("cdn.example.com"), PUBLIC_RESOLVER);

    assertThatCode(() -> validator.validate(
        URI.create("https://cdn.example.com/catalog/shoe.jpg?width=1200")))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsPrivateAddressesEvenForAnAllowedHost() {
    var validator = new TrustedImageUriValidator(
        Set.of("cdn.example.com"),
        host -> List.of(InetAddress.getByName("127.0.0.1")));

    assertRejected(validator, "https://cdn.example.com/shoe.jpg");
  }

  @Test
  void rejectsNonHttpsUnlistedHostsAndUnsafeUriParts() {
    var validator = new TrustedImageUriValidator(Set.of("cdn.example.com"), PUBLIC_RESOLVER);

    assertRejected(validator, "http://cdn.example.com/shoe.jpg");
    assertRejected(validator, "https://other.example.com/shoe.jpg");
    assertRejected(validator, "https://user:password@cdn.example.com/shoe.jpg");
    assertRejected(validator, "https://cdn.example.com:8443/shoe.jpg");
    assertRejected(validator, "https://cdn.example.com/shoe.jpg#fragment");
  }

  @Test
  void mapsResolutionFailuresToSourceUnavailableWithoutLeakingTheHost() {
    var validator = new TrustedImageUriValidator(
        Set.of("cdn.example.com"),
        host -> {
          throw new UnknownHostException("cdn.example.com");
        });

    assertThatThrownBy(() -> validator.validate(
        URI.create("https://cdn.example.com/shoe.jpg")))
        .isInstanceOf(ImageSourceUnavailableException.class)
        .hasMessage("Image source is unavailable")
        .hasMessageNotContaining("cdn.example.com");
  }

  private void assertRejected(TrustedImageUriValidator validator, String value) {
    assertThatThrownBy(() -> validator.validate(URI.create(value)))
        .isInstanceOf(RejectedImageException.class)
        .hasMessage("Image URL is not allowed");
  }
}
