package dev.iseif.listingquality.enrichment.media;

import dev.iseif.listingquality.enrichment.media.exception.ImageSourceUnavailableException;
import dev.iseif.listingquality.enrichment.media.exception.RejectedImageException;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class SafeHttpProductImageLoader implements ProductImageLoader {

  private static final String REJECTED_URL_MESSAGE = "Image URL is not allowed";
  private static final String REJECTED_CONTENT_MESSAGE = "Image content is not allowed";
  private static final String UNAVAILABLE_MESSAGE = "Image source is unavailable";
  private static final Set<Integer> RETRYABLE_STATUSES = Set.of(408, 429, 500, 502, 503, 504);

  private final HttpClient httpClient;
  private final ImageUriValidator uriValidator;
  private final ImageLoadingProperties properties;

  public SafeHttpProductImageLoader(
      HttpClient httpClient,
      ImageUriValidator uriValidator,
      ImageLoadingProperties properties) {
    this.httpClient = httpClient;
    this.uriValidator = uriValidator;
    this.properties = properties;
  }

  @Override
  public ProductImageLoadResult load(List<URI> imageUrls) {
    if (imageUrls == null || imageUrls.isEmpty() || imageUrls.size() > properties.maxImages()) {
      throw new RejectedImageException(REJECTED_URL_MESSAGE);
    }

    var images = new ArrayList<ProductImage>();
    var failures = new ArrayList<RuntimeException>();
    long totalBytes = 0;

    for (int index = 0; index < imageUrls.size(); index++) {
      try {
        ProductImage image = loadOne(imageUrls.get(index), "IMAGE_" + (index + 1));
        long prospectiveTotal = Math.addExact(totalBytes, image.byteCount());
        if (prospectiveTotal > properties.maxTotalBytes()) {
          throw new RejectedImageException(REJECTED_CONTENT_MESSAGE);
        }
        // Only images that fit the shared budget consume it, so one oversized image
        // cannot cascade into rejecting the images that follow it.
        totalBytes = prospectiveTotal;
        images.add(image);
      } catch (ArithmeticException exception) {
        failures.add(new RejectedImageException(REJECTED_CONTENT_MESSAGE, exception));
      } catch (RejectedImageException | ImageSourceUnavailableException exception) {
        failures.add(exception);
      }
    }

    if (images.isEmpty()) {
      throw terminalFailure(failures);
    }

    return new ProductImageLoadResult(
        images,
        failures.stream().map(failure -> ImageLoadWarning.IMAGE_UNAVAILABLE).toList());
  }

  private ProductImage loadOne(URI uri, String imageId) {
    uriValidator.validate(uri);

    for (int attempt = 1; attempt <= properties.maxAttempts(); attempt++) {
      try {
        HttpResponse<InputStream> response = send(uri);
        int status = response.statusCode();
        if (status == 200) {
          try (InputStream body = response.body()) {
            return verify(imageId, response, body);
          }
        }

        try (InputStream ignored = response.body()) {
          // Closing the bounded response body keeps the connection reusable.
        }
        if (!RETRYABLE_STATUSES.contains(status)) {
          throw new RejectedImageException(REJECTED_CONTENT_MESSAGE);
        }
        if (attempt == properties.maxAttempts()) {
          throw new ImageSourceUnavailableException(UNAVAILABLE_MESSAGE);
        }
        waitBeforeRetry();
      } catch (IOException exception) {
        if (attempt == properties.maxAttempts()) {
          throw new ImageSourceUnavailableException(UNAVAILABLE_MESSAGE, exception);
        }
        waitBeforeRetry();
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new ImageSourceUnavailableException(UNAVAILABLE_MESSAGE, exception);
      }
    }

    throw new ImageSourceUnavailableException(UNAVAILABLE_MESSAGE);
  }

  private HttpResponse<InputStream> send(URI uri) throws IOException, InterruptedException {
    HttpRequest request = HttpRequest.newBuilder(uri)
        .timeout(properties.requestTimeout())
        .GET()
        .build();
    return httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
  }

  private ProductImage verify(
      String imageId,
      HttpResponse<InputStream> response,
      InputStream body) throws IOException {
    long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
    if (contentLength > properties.maxImageBytes()) {
      throw new RejectedImageException(REJECTED_CONTENT_MESSAGE);
    }

    byte[] bytes = readBounded(body, properties.maxImageBytes());
    MimeType declaredType = parseMediaType(response);
    MimeType detectedType = detectMediaType(bytes);
    if (!detectedType.equals(declaredType)) {
      throw new RejectedImageException(REJECTED_CONTENT_MESSAGE);
    }

    var decoded = ImageIO.read(new ByteArrayInputStream(bytes));
    if (decoded == null) {
      throw new RejectedImageException(REJECTED_CONTENT_MESSAGE);
    }
    try {
      long pixels = Math.multiplyExact((long) decoded.getWidth(), decoded.getHeight());
      if (pixels > properties.maxPixels()) {
        throw new RejectedImageException(REJECTED_CONTENT_MESSAGE);
      }
    } catch (ArithmeticException exception) {
      throw new RejectedImageException(REJECTED_CONTENT_MESSAGE, exception);
    }

    return new ProductImage(imageId, detectedType, bytes);
  }

  private byte[] readBounded(InputStream input, long limit) throws IOException {
    var output = new ByteArrayOutputStream();
    byte[] buffer = new byte[8_192];
    long count = 0;
    int read;
    while ((read = input.read(buffer)) != -1) {
      count += read;
      if (count > limit) {
        throw new RejectedImageException(REJECTED_CONTENT_MESSAGE);
      }
      output.write(buffer, 0, read);
    }
    return output.toByteArray();
  }

  private MimeType parseMediaType(HttpResponse<?> response) {
    String value = response.headers().firstValue("Content-Type")
        .orElseThrow(() -> new RejectedImageException(REJECTED_CONTENT_MESSAGE));
    String normalized = value.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "image/jpeg" -> MimeTypeUtils.IMAGE_JPEG;
      case "image/png" -> MimeTypeUtils.IMAGE_PNG;
      default -> throw new RejectedImageException(REJECTED_CONTENT_MESSAGE);
    };
  }

  private MimeType detectMediaType(byte[] bytes) {
    if (bytes.length >= 3
        && Byte.toUnsignedInt(bytes[0]) == 0xff
        && Byte.toUnsignedInt(bytes[1]) == 0xd8
        && Byte.toUnsignedInt(bytes[2]) == 0xff) {
      return MimeTypeUtils.IMAGE_JPEG;
    }
    byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
    if (bytes.length >= png.length) {
      for (int index = 0; index < png.length; index++) {
        if (bytes[index] != png[index]) {
          throw new RejectedImageException(REJECTED_CONTENT_MESSAGE);
        }
      }
      return MimeTypeUtils.IMAGE_PNG;
    }
    throw new RejectedImageException(REJECTED_CONTENT_MESSAGE);
  }

  private RuntimeException terminalFailure(List<RuntimeException> failures) {
    if (!failures.isEmpty()
        && failures.stream().allMatch(ImageSourceUnavailableException.class::isInstance)) {
      return failures.getFirst();
    }
    return failures.stream()
        .filter(RejectedImageException.class::isInstance)
        .findFirst()
        .orElseGet(() -> new RejectedImageException(REJECTED_CONTENT_MESSAGE));
  }

  private void waitBeforeRetry() {
    if (properties.retryWait().isZero()) {
      return;
    }
    try {
      Thread.sleep(properties.retryWait());
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new ImageSourceUnavailableException(UNAVAILABLE_MESSAGE, exception);
    }
  }
}
