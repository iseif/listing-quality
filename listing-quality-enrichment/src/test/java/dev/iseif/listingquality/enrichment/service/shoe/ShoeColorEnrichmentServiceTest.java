package dev.iseif.listingquality.enrichment.service.shoe;

import dev.iseif.listingquality.enrichment.media.ImageLoadWarning;
import dev.iseif.listingquality.enrichment.media.ProductImage;
import dev.iseif.listingquality.enrichment.media.ProductImageLoadResult;
import dev.iseif.listingquality.enrichment.media.ProductImageLoader;
import dev.iseif.listingquality.enrichment.model.ExecutionRoute;
import dev.iseif.listingquality.enrichment.model.shoe.*;
import dev.iseif.listingquality.enrichment.prompt.ShoeColorPrompt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.MimeTypeUtils;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ShoeColorEnrichmentServiceTest {

  @Mock
  private ProductImageLoader imageLoader;

  @Mock
  private ShoeColorPrompt prompt;

  @Mock
  private FailoverShoeColorGenerator failover;

  @Mock
  private ShoeColorComparisonPolicy comparison;

  @Test
  void loadsAndRendersOnceThenComparesTheValidatedExecution() {
    var request = new ShoeColorEnrichmentRequest(
        "shoe-123",
        List.of(ShoeColor.GREEN),
        List.of(URI.create("https://cdn.example.com/shoe.jpg")));
    List<ProductImage> images = List.of(new ProductImage(
        "IMAGE_1", MimeTypeUtils.IMAGE_JPEG, new byte[] {1, 2, 3}));
    var loadResult = new ProductImageLoadResult(
        images, List.of(ImageLoadWarning.IMAGE_UNAVAILABLE));
    var validated = new ValidatedShoeColorExtraction(
        ShoeColorOutcome.INCONCLUSIVE, List.of(), List.of());
    var execution = new ShoeColorExecution(validated, ExecutionRoute.PRIMARY);
    var expected = new ShoeColorEnrichmentResponse(
        ShoeColorStatus.NEEDS_SELLER_INPUT,
        List.of(), List.of(), List.of(), List.of(),
        List.of(ShoeColorWarning.IMAGE_UNAVAILABLE),
        false,
        new dev.iseif.listingquality.enrichment.model.ExecutionMetadata(ExecutionRoute.PRIMARY));
    given(imageLoader.load(request.imageUrls())).willReturn(loadResult);
    given(prompt.render(images)).willReturn("rendered prompt");
    given(failover.execute("rendered prompt", images)).willReturn(execution);
    given(comparison.compare(
        request,
        validated,
        List.of(ShoeColorWarning.IMAGE_UNAVAILABLE),
        ExecutionRoute.PRIMARY)).willReturn(expected);

    ShoeColorEnrichmentResponse result = new ShoeColorEnrichmentService(
        imageLoader, prompt, failover, comparison).enrich(request);

    assertThat(result).isSameAs(expected);
    verify(imageLoader).load(request.imageUrls());
    verify(prompt).render(loadResult.images());
    verify(failover).execute("rendered prompt", loadResult.images());
    verify(comparison).compare(
        request,
        validated,
        List.of(ShoeColorWarning.IMAGE_UNAVAILABLE),
        ExecutionRoute.PRIMARY);
  }
}
