package dev.iseif.listingquality.evaluation.config;

import dev.iseif.listingquality.evaluation.collect.GitSourceRevisionProvider;
import dev.iseif.listingquality.evaluation.collect.RuntimeEnvironmentProvider;
import dev.iseif.listingquality.evaluation.collect.SourceRevisionProvider;
import dev.iseif.listingquality.evaluation.collect.SystemRuntimeEnvironmentProvider;
import dev.iseif.listingquality.evaluation.command.EvaluationCommandDispatcher;
import dev.iseif.listingquality.evaluation.dataset.DatasetLoader;
import org.springframework.ai.evaluation.Evaluator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class EvaluationConfiguration {

  @Bean
  Clock evaluationClock() {
    return Clock.systemUTC();
  }

  @Bean
  SourceRevisionProvider sourceRevisionProvider() {
    return GitSourceRevisionProvider.discover(Path.of(System.getProperty("user.dir")));
  }

  @Bean
  RuntimeEnvironmentProvider runtimeEnvironmentProvider() {
    return new SystemRuntimeEnvironmentProvider();
  }

  @Bean
  EvaluationCommandDispatcher evaluationCommandDispatcher(
      ObjectMapper objectMapper,
      DatasetLoader datasetLoader,
      RestClient.Builder restClientBuilder,
      ObjectProvider<Evaluator> evaluatorProvider,
      SourceRevisionProvider sourceRevisionProvider,
      RuntimeEnvironmentProvider runtimeEnvironmentProvider,
      Clock evaluationClock) {
    return new EvaluationCommandDispatcher(
        objectMapper,
        datasetLoader,
        restClientBuilder,
        evaluatorProvider::getIfAvailable,
        sourceRevisionProvider,
        runtimeEnvironmentProvider,
        evaluationClock);
  }
}
