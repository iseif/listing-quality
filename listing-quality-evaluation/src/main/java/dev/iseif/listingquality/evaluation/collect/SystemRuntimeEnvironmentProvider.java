package dev.iseif.listingquality.evaluation.collect;

import dev.iseif.listingquality.evaluation.result.RuntimeEnvironment;

import java.lang.management.ManagementFactory;

public final class SystemRuntimeEnvironmentProvider implements RuntimeEnvironmentProvider {

  @Override
  public RuntimeEnvironment capture(String runtime, String runtimeVersion) {
    long memory = Runtime.getRuntime().maxMemory();
    if (ManagementFactory.getOperatingSystemMXBean()
        instanceof com.sun.management.OperatingSystemMXBean operatingSystem) {
      memory = operatingSystem.getTotalMemorySize();
    }
    String architecture = System.getProperty("os.arch", "unknown");
    return new RuntimeEnvironment(
        runtime,
        runtimeVersion,
        System.getProperty("os.name", "unknown"),
        architecture,
        architecture,
        memory,
        System.getProperty("java.version", "unknown"));
  }
}
