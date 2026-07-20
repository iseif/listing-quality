package dev.iseif.listingquality.enrichment.media;

import dev.iseif.listingquality.enrichment.media.exception.ImageSourceUnavailableException;
import dev.iseif.listingquality.enrichment.media.exception.RejectedImageException;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class TrustedImageUriValidator implements ImageUriValidator {

  private static final String REJECTED_MESSAGE = "Image URL is not allowed";
  private static final String UNAVAILABLE_MESSAGE = "Image source is unavailable";

  private final Set<String> allowedHosts;
  private final HostAddressResolver resolver;

  public TrustedImageUriValidator(ImageLoadingProperties properties) {
    this(properties.allowedHosts(), host -> Arrays.asList(InetAddress.getAllByName(host)));
  }

  TrustedImageUriValidator(Set<String> allowedHosts, HostAddressResolver resolver) {
    this.allowedHosts = allowedHosts.stream()
        .map(host -> host.toLowerCase(Locale.ROOT))
        .collect(Collectors.toUnmodifiableSet());
    this.resolver = resolver;
  }

  @Override
  public void validate(URI uri) {
    if (!hasAllowedShape(uri)) {
      throw new RejectedImageException(REJECTED_MESSAGE);
    }

    List<InetAddress> addresses;
    try {
      addresses = resolver.resolve(uri.getHost());
    } catch (UnknownHostException exception) {
      throw new ImageSourceUnavailableException(UNAVAILABLE_MESSAGE, exception);
    }

    if (addresses.isEmpty() || addresses.stream().anyMatch(address -> !isPublic(address))) {
      throw new RejectedImageException(REJECTED_MESSAGE);
    }
  }

  private boolean hasAllowedShape(URI uri) {
    if (uri == null || !uri.isAbsolute() || uri.getHost() == null) {
      return false;
    }
    String host = uri.getHost().toLowerCase(Locale.ROOT);
    return "https".equalsIgnoreCase(uri.getScheme())
        && allowedHosts.contains(host)
        && uri.getRawUserInfo() == null
        && uri.getRawFragment() == null
        && (uri.getPort() == -1 || uri.getPort() == 443);
  }

  private boolean isPublic(InetAddress address) {
    if (address.isAnyLocalAddress()
        || address.isLoopbackAddress()
        || address.isLinkLocalAddress()
        || address.isSiteLocalAddress()
        || address.isMulticastAddress()) {
      return false;
    }

    byte[] bytes = address.getAddress();
    return bytes.length == 4 ? isPublicIpv4(bytes) : isPublicIpv6(bytes);
  }

  private boolean isPublicIpv4(byte[] bytes) {
    int first = Byte.toUnsignedInt(bytes[0]);
    int second = Byte.toUnsignedInt(bytes[1]);
    int third = Byte.toUnsignedInt(bytes[2]);

    return first != 0
        && first != 10
        && first != 127
        && !(first == 100 && second >= 64 && second <= 127)
        && !(first == 169 && second == 254)
        && !(first == 172 && second >= 16 && second <= 31)
        && !(first == 192 && second == 0 && third == 0)
        && !(first == 192 && second == 0 && third == 2)
        && !(first == 192 && second == 168)
        && !(first == 198 && (second == 18 || second == 19))
        && !(first == 198 && second == 51 && third == 100)
        && !(first == 203 && second == 0 && third == 113)
        && first < 224;
  }

  private boolean isPublicIpv6(byte[] bytes) {
    int first = Byte.toUnsignedInt(bytes[0]);
    return (first & 0xfe) != 0xfc
        && !(first == 0x20
            && Byte.toUnsignedInt(bytes[1]) == 0x01
            && Byte.toUnsignedInt(bytes[2]) == 0x0d
            && Byte.toUnsignedInt(bytes[3]) == 0xb8);
  }
}
