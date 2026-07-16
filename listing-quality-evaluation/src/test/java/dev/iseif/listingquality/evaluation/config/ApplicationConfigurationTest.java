package dev.iseif.listingquality.evaluation.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationConfigurationTest {

  @Test
  void defaultCliStartupDisablesEverySpringAiModelType() throws IOException {
    MockEnvironment environment = new MockEnvironment();
    new YamlPropertySourceLoader()
        .load("application", new ClassPathResource("application.yaml"))
        .forEach(source -> environment.getPropertySources().addLast(source));

    assertThat(environment.getProperty("spring.ai.model.chat")).isEqualTo("none");
    assertThat(environment.getProperty("spring.ai.model.audio.speech")).isEqualTo("none");
    assertThat(environment.getProperty("spring.ai.model.audio.transcription")).isEqualTo("none");
    assertThat(environment.getProperty("spring.ai.model.embedding")).isEqualTo("none");
    assertThat(environment.getProperty("spring.ai.model.image")).isEqualTo("none");
    assertThat(environment.getProperty("spring.ai.model.moderation")).isEqualTo("none");
  }

  @Test
  void semanticJudgeExplainsHowToScoreConceptsAlreadyPresentInTheListing()
      throws IOException {
    String rubric = new ClassPathResource("prompts/listing-review-judge-system.st")
        .getContentAsString(StandardCharsets.UTF_8);
    String normalizedRubric = rubric.replaceAll("\\s+", " ");

    assertThat(normalizedRubric)
        .contains("decide whether the listing already provides it")
        .contains("Do not require the response to repeat or praise information")
        .contains("correctly leaves it unflagged");
  }

  @Test
  void openAiJudgeProfileUsesReliableGpt56SolSettings() throws IOException {
    MockEnvironment environment = new MockEnvironment();
    new YamlPropertySourceLoader()
        .load("judge-openai", new ClassPathResource("application-judge-openai.yaml"))
        .forEach(source -> environment.getPropertySources().addLast(source));

    assertThat(environment.getProperty("spring.ai.openai.chat.model"))
        .isEqualTo("gpt-5.6-sol");
    assertThat(environment.getProperty(
        "spring.ai.openai.chat.max-completion-tokens", Integer.class))
        .isEqualTo(1000);
    assertThat(environment.getProperty("spring.ai.openai.chat.temperature", Double.class))
        .isEqualTo(1.0);
  }
}
