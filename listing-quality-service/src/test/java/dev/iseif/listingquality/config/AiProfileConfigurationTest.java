package dev.iseif.listingquality.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiProfileConfigurationTest {

  @Test
  void ollamaProfileUsesNativeChatModelAndGemma4() throws IOException {
    ConfigurableEnvironment environment = loadProfile("ollama");

    assertThat(environment.getProperty("spring.ai.model.chat")).isEqualTo("ollama");
    assertThat(environment.getProperty("spring.ai.ollama.base-url"))
        .isEqualTo("http://localhost:11434");
    assertThat(environment.getProperty("spring.ai.ollama.chat.model"))
        .isEqualTo("gemma4:e4b");
    assertThat(environment.getProperty("spring.ai.ollama.chat.num-predict", Integer.class))
        .isEqualTo(800);
    assertThat(environment.containsProperty("spring.ai.ollama.chat.options.num-predict"))
        .isFalse();
  }

  @Test
  void omlxProfileUsesOpenAiCompatibilityAndMlxGemma4() throws IOException {
    ConfigurableEnvironment environment = loadProfile("omlx");

    assertThat(environment.getProperty("spring.ai.model.chat")).isEqualTo("openai");
    assertThat(environment.getProperty("spring.ai.openai.chat.base-url"))
        .isEqualTo("http://localhost:8000/v1");
    assertThat(environment.getProperty("spring.ai.openai.chat.api-key")).isEqualTo("local");
    assertThat(environment.getProperty("spring.ai.openai.chat.model"))
        .isEqualTo("mlx-community/gemma-4-e4b-it-4bit");
    assertThat(environment.getProperty("spring.ai.openai.chat.max-tokens", Integer.class))
        .isEqualTo(800);
    assertThat(environment.containsProperty("spring.ai.openai.chat.options.max-tokens"))
        .isFalse();
  }

  @Test
  void cloudProfilesUseSpringAiTwoDirectChatProperties() throws IOException {
    ConfigurableEnvironment openAi = loadProfile("openai");
    ConfigurableEnvironment gemini = loadProfile("gemini");

    assertThat(openAi.getProperty("listing-quality.ai.temperature", Double.class))
        .isEqualTo(1.0);
    assertThat(openAi.getProperty("spring.ai.openai.chat.max-completion-tokens", Integer.class))
        .isEqualTo(800);
    assertThat(openAi.containsProperty(
        "spring.ai.openai.chat.options.max-completion-tokens"))
        .isFalse();
    assertThat(gemini.getProperty(
        "spring.ai.google.genai.chat.max-output-tokens", Integer.class))
        .isEqualTo(800);
    assertThat(gemini.getProperty("spring.ai.google.genai.chat.thinking-level"))
        .isEqualTo("MINIMAL");
    assertThat(gemini.containsProperty(
        "spring.ai.google.genai.chat.options.max-output-tokens"))
        .isFalse();
  }

  @Test
  void cloudProfilesAllowExperimentModelOverridesWhilePreservingDefaults() throws IOException {
    ConfigurableEnvironment openAi = loadProfile(
        "openai", Map.of("OPENAI_CHAT_MODEL", "gpt-5.6"));
    ConfigurableEnvironment gemini = loadProfile(
        "gemini", Map.of("GEMINI_CHAT_MODEL", "gemini-3.5-flash"));

    assertThat(openAi.getProperty("spring.ai.openai.chat.model"))
        .isEqualTo("gpt-5.6");
    assertThat(gemini.getProperty("spring.ai.google.genai.chat.model"))
        .isEqualTo("gemini-3.5-flash");
  }

  private ConfigurableEnvironment loadProfile(String profile) throws IOException {
    return loadProfile(profile, Map.of());
  }

  private ConfigurableEnvironment loadProfile(
      String profile, Map<String, String> overrides) throws IOException {
    Resource resource = new ClassPathResource("application-%s.yaml".formatted(profile));
    assertThat(resource.exists())
        .as("configuration resource for the %s profile", profile)
        .isTrue();

    MockEnvironment environment = new MockEnvironment();
    overrides.forEach(environment::setProperty);
    new YamlPropertySourceLoader()
        .load(profile, resource)
        .forEach(propertySource -> environment.getPropertySources().addLast(propertySource));
    return environment;
  }
}
