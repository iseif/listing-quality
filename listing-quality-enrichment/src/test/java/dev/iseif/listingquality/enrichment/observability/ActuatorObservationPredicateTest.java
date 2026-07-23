package dev.iseif.listingquality.enrichment.observability;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.observation.ServerRequestObservationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class ActuatorObservationPredicateTest {

  private final ActuatorObservationPredicate predicate = new ActuatorObservationPredicate();

  @Test
  void excludesActuatorRequestsButKeepsApplicationRequests() {
    assertThat(predicate.test("http.server.requests", context("/actuator/prometheus"))).isFalse();
    assertThat(predicate.test("http.server.requests", context("/api/enrichments/books"))).isTrue();
  }

  private ServerRequestObservationContext context(String requestUri) {
    HttpServletRequest request = mock(HttpServletRequest.class);
    given(request.getRequestURI()).willReturn(requestUri);
    return new ServerRequestObservationContext(request, mock(HttpServletResponse.class));
  }
}
