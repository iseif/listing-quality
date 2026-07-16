package dev.iseif.listingquality.evaluation.collect;

import dev.iseif.listingquality.evaluation.result.TokenUsage;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TokenMetricReaderTest {

  @Test
  void readsTokenCountersAndResponseModel() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    TokenMetricReader reader = new TokenMetricReader(builder, URI.create("http://localhost:8080"));

    expectBase(server, "gpt-5.6");
    expectValue(server, "input", 120);
    expectValue(server, "output", 45);
    expectValue(server, "total", 165);

    TokenMetricSnapshot snapshot = reader.read();

    assertThat(snapshot.usage()).isEqualTo(new TokenUsage(120L, 45L, 165L));
    assertThat(snapshot.responseModel()).isEqualTo("gpt-5.6");
    server.verify();
  }

  @Test
  void missingMetricIsNotReportedRatherThanZero() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    TokenMetricReader reader = new TokenMetricReader(builder, URI.create("http://localhost:8080"));
    server.expect(once(), requestTo(
        "http://localhost:8080/actuator/metrics/gen_ai.client.token.usage"))
        .andRespond(withResourceNotFound());

    assertThat(reader.read()).isEqualTo(
        new TokenMetricSnapshot(TokenUsage.notReported(), null));
  }

  private void expectBase(MockRestServiceServer server, String model) {
    server.expect(once(), requestTo(
        "http://localhost:8080/actuator/metrics/gen_ai.client.token.usage"))
        .andRespond(withSuccess("""
            {"name":"gen_ai.client.token.usage","measurements":[],"availableTags":[
              {"tag":"gen_ai.response.model","values":["%s"]},
              {"tag":"gen_ai.token.type","values":["input","output","total"]}
            ]}
            """.formatted(model), MediaType.APPLICATION_JSON));
  }

  private void expectValue(MockRestServiceServer server, String type, long value) {
    server.expect(once(), requestTo(
        "http://localhost:8080/actuator/metrics/gen_ai.client.token.usage?tag=gen_ai.token.type:%s"
            .formatted(type)))
        .andRespond(withSuccess("""
            {"name":"gen_ai.client.token.usage",
             "measurements":[{"statistic":"TOTAL","value":%d}],"availableTags":[]}
            """.formatted(value), MediaType.APPLICATION_JSON));
  }
}
