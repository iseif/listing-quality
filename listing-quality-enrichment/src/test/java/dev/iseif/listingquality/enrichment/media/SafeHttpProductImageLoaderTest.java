package dev.iseif.listingquality.enrichment.media;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.iseif.listingquality.enrichment.media.exception.RejectedImageException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.MimeTypeUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SafeHttpProductImageLoaderTest {

  private HttpServer server;

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
  void loadsVerifiedJpegAndPngImagesWithStableIds() throws IOException {
    server.createContext("/front.jpg", exchange ->
        respond(exchange, 200, "image/jpeg", imageBytes("jpg", 2, 2)));
    server.createContext("/side.png", exchange ->
        respond(exchange, 200, "image/png", imageBytes("png", 2, 2)));

    ProductImageLoadResult result = loader(defaultProperties()).load(List.of(
        uri("/front.jpg"), uri("/side.png")));

    assertThat(result.warnings()).isEmpty();
    assertThat(result.images()).extracting(ProductImage::imageId)
        .containsExactly("IMAGE_1", "IMAGE_2");
    assertThat(result.images()).extracting(ProductImage::mediaType)
        .containsExactly(MimeTypeUtils.IMAGE_JPEG, MimeTypeUtils.IMAGE_PNG);
  }

  @Test
  void retriesA503OnceThenReturnsTheVerifiedImage() throws IOException {
    AtomicInteger attempts = new AtomicInteger();
    byte[] jpeg = imageBytes("jpg", 2, 2);
    server.createContext("/shoe.jpg", exchange -> {
      if (attempts.incrementAndGet() == 1) {
        respond(exchange, 503, "text/plain", new byte[0]);
      } else {
        respond(exchange, 200, "image/jpeg", jpeg);
      }
    });

    ProductImageLoadResult result = loader(defaultProperties()).load(List.of(uri("/shoe.jpg")));

    assertThat(result.images()).singleElement()
        .satisfies(image -> assertThat(image.imageId()).isEqualTo("IMAGE_1"));
    assertThat(attempts).hasValue(2);
  }

  @Test
  void doesNotFollowRedirectsOrRetryPermanentFailures() {
    AtomicInteger redirects = new AtomicInteger();
    AtomicInteger missing = new AtomicInteger();
    server.createContext("/redirect", exchange -> {
      redirects.incrementAndGet();
      exchange.getResponseHeaders().add("Location", uri("/shoe.jpg").toString());
      respond(exchange, 302, "text/plain", new byte[0]);
    });
    server.createContext("/missing", exchange -> {
      missing.incrementAndGet();
      respond(exchange, 404, "text/plain", new byte[0]);
    });

    assertThatThrownBy(() -> loader(defaultProperties()).load(List.of(uri("/redirect"))))
        .isInstanceOf(RejectedImageException.class);
    assertThatThrownBy(() -> loader(defaultProperties()).load(List.of(uri("/missing"))))
        .isInstanceOf(RejectedImageException.class);
    assertThat(redirects).hasValue(1);
    assertThat(missing).hasValue(1);
  }

  @Test
  void rejectsContentWhoseSignatureDoesNotMatchItsMediaType() {
    server.createContext("/fake.jpg", exchange ->
        respond(exchange, 200, "image/jpeg", "not-an-image".getBytes()));

    assertThatThrownBy(() -> loader(defaultProperties()).load(List.of(uri("/fake.jpg"))))
        .isInstanceOf(RejectedImageException.class)
        .hasMessage("Image content is not allowed");
  }

  @Test
  void rejectsOversizedBytesAndExcessivePixelCount() throws IOException {
    byte[] png = imageBytes("png", 2, 2);
    server.createContext("/large-bytes.png", exchange ->
        respond(exchange, 200, "image/png", png));
    server.createContext("/large-pixels.png", exchange ->
        respond(exchange, 200, "image/png", png));

    assertThatThrownBy(() -> loader(properties(16, 1_024, 25_000_000))
        .load(List.of(uri("/large-bytes.png"))))
        .isInstanceOf(RejectedImageException.class);
    assertThatThrownBy(() -> loader(properties(1_024, 1_024, 3))
        .load(List.of(uri("/large-pixels.png"))))
        .isInstanceOf(RejectedImageException.class);
  }

  @Test
  void returnsPartialSuccessWithACategoryNeutralWarning() throws IOException {
    server.createContext("/valid.jpg", exchange ->
        respond(exchange, 200, "image/jpeg", imageBytes("jpg", 2, 2)));
    server.createContext("/missing.jpg", exchange ->
        respond(exchange, 404, "text/plain", new byte[0]));

    ProductImageLoadResult result = loader(defaultProperties()).load(List.of(
        uri("/valid.jpg"), uri("/missing.jpg")));

    assertThat(result.images()).extracting(ProductImage::imageId).containsExactly("IMAGE_1");
    assertThat(result.warnings()).containsExactly(ImageLoadWarning.IMAGE_UNAVAILABLE);
  }

  @Test
  void anImageThatExceedsTheTotalBudgetDoesNotRejectTheImagesAfterIt() throws IOException {
    byte[] small = imageBytes("jpg", 2, 2);
    byte[] large = noiseImageBytes("png", 256, 256);
    assertThat(large.length).isGreaterThan(2 * small.length);
    server.createContext("/first.jpg", exchange ->
        respond(exchange, 200, "image/jpeg", small));
    server.createContext("/large.png", exchange ->
        respond(exchange, 200, "image/png", large));
    server.createContext("/third.jpg", exchange ->
        respond(exchange, 200, "image/jpeg", small));

    // The budget admits the two small images together, but not the large one.
    long totalBudget = (long) small.length + large.length - 1;
    ProductImageLoadResult result = loader(properties(large.length, totalBudget, 25_000_000))
        .load(List.of(uri("/first.jpg"), uri("/large.png"), uri("/third.jpg")));

    assertThat(result.images()).extracting(ProductImage::imageId)
        .containsExactly("IMAGE_1", "IMAGE_3");
    assertThat(result.warnings()).containsExactly(ImageLoadWarning.IMAGE_UNAVAILABLE);
  }

  @Test
  void productImageDefensivelyCopiesItsBytes() {
    byte[] bytes = {1, 2, 3};
    ProductImage image = new ProductImage("IMAGE_1", MimeTypeUtils.IMAGE_JPEG, bytes);

    bytes[0] = 9;
    byte[] returned = image.bytes();
    returned[1] = 9;

    assertThat(image.bytes()).containsExactly(1, 2, 3);
    assertThat(image.byteCount()).isEqualTo(3);
  }

  private SafeHttpProductImageLoader loader(ImageLoadingProperties properties) {
    HttpClient client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .connectTimeout(properties.connectTimeout())
        .build();
    ImageUriValidator acceptTestServer = uri -> { };
    return new SafeHttpProductImageLoader(client, acceptTestServer, properties);
  }

  private ImageLoadingProperties defaultProperties() {
    return properties(1_024, 2_048, 25_000_000);
  }

  private ImageLoadingProperties properties(long maxImageBytes, long maxTotalBytes, long maxPixels) {
    return new ImageLoadingProperties(
        Set.of("cdn.example.com"), 3, maxImageBytes, maxTotalBytes, maxPixels,
        Duration.ofSeconds(1), Duration.ofSeconds(2), 2, Duration.ZERO);
  }

  private URI uri(String path) {
    return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
  }

  /** Random pixels keep the encoded payload large, because a blank image compresses away. */
  private byte[] noiseImageBytes(String format, int width, int height) throws IOException {
    var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    var random = new Random(42);
    for (int x = 0; x < width; x++) {
      for (int y = 0; y < height; y++) {
        image.setRGB(x, y, random.nextInt(0x01000000));
      }
    }
    var output = new ByteArrayOutputStream();
    ImageIO.write(image, format, output);
    return output.toByteArray();
  }

  private byte[] imageBytes(String format, int width, int height) throws IOException {
    var output = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, output);
    return output.toByteArray();
  }

  private void respond(HttpExchange exchange, int status, String contentType, byte[] body)
      throws IOException {
    exchange.getResponseHeaders().set("Content-Type", contentType);
    exchange.sendResponseHeaders(status, body.length);
    try (var responseBody = exchange.getResponseBody()) {
      responseBody.write(body);
    }
  }
}
