package dev.iseif.listingquality.evaluation.grade;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.EntityParamSpec;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.ai.evaluation.Evaluator;
import org.springframework.core.io.Resource;

import java.util.Map;

public final class SemanticListingEvaluator implements Evaluator {

  private final ChatClient chatClient;
  private final PromptTemplate promptTemplate;

  public SemanticListingEvaluator(ChatClient chatClient, Resource promptResource) {
    this.chatClient = chatClient;
    this.promptTemplate = new PromptTemplate(promptResource);
  }

  @Override
  public EvaluationResponse evaluate(EvaluationRequest request) {
    String prompt = promptTemplate.render(Map.of(
        "evaluationContext", request.getUserText(),
        "responseContent", request.getResponseContent()));
    SemanticEvaluation evaluation = chatClient.prompt()
        .user(prompt)
        .call()
        .entity(SemanticEvaluation.class, EntityParamSpec::validateSchema);
    if (evaluation == null) {
      throw new IllegalStateException("Judge returned no semantic evaluation");
    }
    return new EvaluationResponse(
        evaluation.pass(),
        evaluation.expectedConceptCoverage() / 100.0f,
        evaluation.explanation(),
        Map.of(
            "expectedConceptCoverage", evaluation.expectedConceptCoverage(),
            "unsupportedIssueScore", evaluation.unsupportedIssueScore(),
            "groundednessScore", evaluation.groundednessScore(),
            "suggestionUsefulness", evaluation.suggestionUsefulness()));
  }
}
