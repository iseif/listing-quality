package dev.iseif.listingquality.evaluation.collect;

import dev.iseif.listingquality.evaluation.result.RuntimeEnvironment;

@FunctionalInterface
public interface RuntimeEnvironmentProvider {
  RuntimeEnvironment capture(String runtime, String runtimeVersion);
}
