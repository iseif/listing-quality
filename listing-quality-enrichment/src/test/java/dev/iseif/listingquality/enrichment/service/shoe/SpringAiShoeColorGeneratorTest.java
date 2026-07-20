package dev.iseif.listingquality.enrichment.service.shoe;

import dev.iseif.listingquality.enrichment.media.ProductImage;
import dev.iseif.listingquality.enrichment.model.shoe.GeneratedShoeColorExtraction;
import dev.iseif.listingquality.enrichment.model.shoe.ShoeColorOutcome;
import dev.iseif.listingquality.enrichment.service.execution.ModelExecutionException;
import dev.iseif.listingquality.enrichment.service.execution.ModelFailureCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.client.ChatClient.PromptUserSpec;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.content.Media;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.util.MimeTypeUtils;

import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SpringAiShoeColorGeneratorTest {

  @Mock
  private ChatClient chatClient;

  @Mock
  private ChatClientRequestSpec requestSpec;

  @Mock
  private CallResponseSpec responseSpec;

  @Mock
  private PromptUserSpec userSpec;

  private SpringAiShoeColorGenerator generator;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    given(chatClient.prompt()).willReturn(requestSpec);
    given(requestSpec.user(any(Consumer.class))).willReturn(requestSpec);
    given(requestSpec.advisors(any(Advisor.class))).willReturn(requestSpec);
    given(requestSpec.call()).willReturn(responseSpec);
    generator = new SpringAiShoeColorGenerator("omlx", chatClient, 2);
  }

  @Test
  @SuppressWarnings("unchecked")
  void sendsVerifiedBytesAsNamedMultimodalMedia() {
    var expected = new GeneratedShoeColorExtraction(
        ShoeColorOutcome.INCONCLUSIVE, List.of(), List.of());
    given(responseSpec.entity(GeneratedShoeColorExtraction.class)).willReturn(expected);
    given(userSpec.text("analyze IMAGE_1 and IMAGE_2")).willReturn(userSpec);
    List<ProductImage> images = List.of(image("IMAGE_1"), image("IMAGE_2"));

    assertThat(generator.generate("analyze IMAGE_1 and IMAGE_2", images)).isSameAs(expected);

    var userCaptor = ArgumentCaptor.forClass(Consumer.class);
    verify(requestSpec).user(userCaptor.capture());
    ((Consumer<PromptUserSpec>) userCaptor.getValue()).accept(userSpec);
    verify(userSpec).text("analyze IMAGE_1 and IMAGE_2");
    var mediaCaptor = ArgumentCaptor.forClass(Media[].class);
    verify(userSpec).media(mediaCaptor.capture());
    assertThat(mediaCaptor.getValue()).extracting(Media::getId)
        .containsExactly("IMAGE_1", "IMAGE_2");
  }

  @Test
  void classifiesMalformedOutputAsFallbackEligible() {
    given(responseSpec.entity(GeneratedShoeColorExtraction.class))
        .willThrow(new RuntimeException("malformed response"));

    assertThatThrownBy(() -> generator.generate("prompt", List.of(image("IMAGE_1"))))
        .isInstanceOfSatisfying(ModelExecutionException.class, failure -> {
          assertThat(failure.providerId()).isEqualTo("omlx");
          assertThat(failure.category()).isEqualTo(ModelFailureCategory.INVALID_MODEL_OUTPUT);
          assertThat(failure.eligible()).isTrue();
        });
  }

  @Test
  void classifiesProviderFailuresConsistentlyWithBookGeneration() {
    given(responseSpec.entity(GeneratedShoeColorExtraction.class))
        .willThrow(new TransientAiException("provider unavailable"))
        .willThrow(new NonTransientAiException("configuration invalid"));

    assertThatThrownBy(() -> generator.generate("prompt", List.of(image("IMAGE_1"))))
        .isInstanceOfSatisfying(ModelExecutionException.class, failure -> {
          assertThat(failure.category()).isEqualTo(ModelFailureCategory.PROVIDER_UNAVAILABLE);
          assertThat(failure.eligible()).isTrue();
        });
    assertThatThrownBy(() -> generator.generate("prompt", List.of(image("IMAGE_1"))))
        .isInstanceOfSatisfying(ModelExecutionException.class, failure -> {
          assertThat(failure.category()).isEqualTo(ModelFailureCategory.CONFIGURATION_ERROR);
          assertThat(failure.eligible()).isFalse();
        });
  }

  private ProductImage image(String id) {
    return new ProductImage(id, MimeTypeUtils.IMAGE_JPEG, new byte[] {1, 2, 3});
  }
}
