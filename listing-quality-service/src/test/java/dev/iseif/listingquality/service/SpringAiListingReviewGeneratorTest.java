package dev.iseif.listingquality.service;

import dev.iseif.listingquality.model.ListingReview;
import dev.iseif.listingquality.service.exception.AiProviderException;
import dev.iseif.listingquality.service.exception.AiProviderUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.retry.TransientAiException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SpringAiListingReviewGeneratorTest {

  @Mock
  private ChatClient chatClient;

  @Mock
  private ChatClientRequestSpec requestSpec;

  @Mock
  private CallResponseSpec responseSpec;

  private SpringAiListingReviewGenerator generator;

  @BeforeEach
  void setUp() {
    given(chatClient.prompt()).willReturn(requestSpec);
    given(requestSpec.user(anyString())).willReturn(requestSpec);
    given(requestSpec.call()).willReturn(responseSpec);

    generator = new SpringAiListingReviewGenerator(chatClient);
  }

  @Test
  void returnsTheEntityProducedByTheChatClient() {
    ListingReview expected = new ListingReview(80, List.of(), List.of(), List.of("Add layout"), false);
    given(responseSpec.entity(eq(ListingReview.class), any())).willReturn(expected);

    assertThat(generator.generate("prompt")).isSameAs(expected);
  }

  @Test
  void translatesTransientFailureIntoProviderUnavailable() {
    given(responseSpec.entity(eq(ListingReview.class), any()))
        .willThrow(new TransientAiException("temporarily down"));

    assertThatThrownBy(() -> generator.generate("prompt"))
        .isInstanceOf(AiProviderUnavailableException.class);
  }

  @Test
  void translatesOtherRuntimeFailureIntoProviderException() {
    given(responseSpec.entity(eq(ListingReview.class), any()))
        .willThrow(new RuntimeException("malformed response"));

    assertThatThrownBy(() -> generator.generate("prompt"))
        .isInstanceOf(AiProviderException.class)
        .isNotInstanceOf(AiProviderUnavailableException.class);
  }
}
