package dev.iseif.listingquality.enrichment.service.execution;

public enum ModelFailureCategory {
  PROVIDER_UNAVAILABLE,
  RATE_LIMITED,
  QUOTA_EXHAUSTED,
  INVALID_MODEL_OUTPUT,
  AUTHENTICATION_FAILED,
  CONFIGURATION_ERROR,
  SAFETY_REFUSAL
}
