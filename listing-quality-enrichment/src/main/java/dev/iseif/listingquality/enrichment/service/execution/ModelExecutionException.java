package dev.iseif.listingquality.enrichment.service.execution;

import java.util.Objects;

public final class ModelExecutionException extends RuntimeException {

  private static final String PUBLIC_MESSAGE = "AI model execution failed";

  private final String providerId;
  private final ModelFailureCategory category;
  private final boolean eligible;

  private ModelExecutionException(
      String providerId,
      ModelFailureCategory category,
      boolean eligible,
      Throwable cause) {
    super(PUBLIC_MESSAGE, cause);
    this.providerId = Objects.requireNonNull(providerId);
    this.category = Objects.requireNonNull(category);
    this.eligible = eligible;
  }

  public static ModelExecutionException eligible(
      String providerId,
      ModelFailureCategory category,
      Throwable cause) {
    return new ModelExecutionException(providerId, category, true, cause);
  }

  public static ModelExecutionException ineligible(
      String providerId,
      ModelFailureCategory category,
      Throwable cause) {
    return new ModelExecutionException(providerId, category, false, cause);
  }

  public String providerId() {
    return providerId;
  }

  public ModelFailureCategory category() {
    return category;
  }

  public boolean eligible() {
    return eligible;
  }
}
