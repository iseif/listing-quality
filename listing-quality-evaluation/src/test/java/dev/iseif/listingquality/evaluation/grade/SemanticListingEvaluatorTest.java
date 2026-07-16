package dev.iseif.listingquality.evaluation.grade;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SemanticListingEvaluatorTest {

  @Mock
  private ChatClient chatClient;
  @Mock
  private ChatClientRequestSpec requestSpec;
  @Mock
  private CallResponseSpec responseSpec;

  private SemanticListingEvaluator evaluator;

  @BeforeEach
  void setUp() {
    given(chatClient.prompt()).willReturn(requestSpec);
    given(requestSpec.user(any(String.class))).willReturn(requestSpec);
    given(requestSpec.call()).willReturn(responseSpec);
    evaluator = new SemanticListingEvaluator(
        chatClient, new ClassPathResource("prompts/listing-review-judge.st"));
  }

  @Test
  void evaluatesOneAnonymousResponseAgainstTheFixedRubric() {
    given(responseSpec.entity(eq(SemanticEvaluation.class), any()))
        .willReturn(new SemanticEvaluation(85, 90, 95, 80, true,
            "Covered the important missing concepts without inventing facts."));
    EvaluationRequest request = new EvaluationRequest(
        """
        {"candidate":"candidate-1","caseId":"book-missing-author-isbn",
         "listing":{"title":"Clean Code Paperback"},
         "expectations":{"expectedConcepts":["author","isbn"]}}
        """,
        """
        {"qualityScore":50,"missingFields":["author","ISBN"],
         "issues":[],"suggestions":["Add author and ISBN"],"requiresHumanReview":false}
        """);

    EvaluationResponse response = evaluator.evaluate(request);

    assertThat(response.isPass()).isTrue();
    assertThat(response.getScore()).isEqualTo(0.85f);
    assertThat(response.getFeedback()).contains("important missing concepts");
    assertThat(response.getMetadata())
        .containsEntry("expectedConceptCoverage", 85)
        .containsEntry("groundednessScore", 95);
    ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
    verify(requestSpec).user(prompt.capture());
    assertThat(prompt.getValue())
        .contains("candidate-1", "book-missing-author-isbn", "author", "ISBN")
        .doesNotContain("OpenAI", "Gemini", "Ollama", "oMLX", "gpt-5.6");
  }
}
