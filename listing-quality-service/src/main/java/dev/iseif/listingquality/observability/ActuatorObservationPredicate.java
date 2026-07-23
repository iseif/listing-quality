package dev.iseif.listingquality.observability;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.stereotype.Component;

/**
 * Management traffic is never part of the application's request telemetry.
 *
 * <p>This applies in every profile on purpose. The predicate filters observations, not only
 * traces, so gating it would make {@code http.server.requests} count scrape traffic in one
 * deployment and ignore it in another.
 */
@Component
final class ActuatorObservationPredicate implements ObservationPredicate {

  // The actuator base path is a fixed convention here, not a value worth externalizing.
  @SuppressWarnings("java:S1075")
  private static final String ACTUATOR_PATH = "/actuator/";

  @Override
  public boolean test(String observationName, Observation.Context context) {
    if (!(context instanceof ServerRequestObservationContext serverRequest)) {
      return true;
    }
    HttpServletRequest request = serverRequest.getCarrier();
    return request == null || !request.getRequestURI().startsWith(ACTUATOR_PATH);
  }
}
