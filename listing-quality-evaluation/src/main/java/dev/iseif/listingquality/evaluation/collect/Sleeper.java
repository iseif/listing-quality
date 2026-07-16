package dev.iseif.listingquality.evaluation.collect;

import java.time.Duration;

@FunctionalInterface
public interface Sleeper {
  void sleep(Duration duration) throws InterruptedException;

  static Sleeper threadSleep() {
    return duration -> Thread.sleep(duration.toMillis());
  }
}
