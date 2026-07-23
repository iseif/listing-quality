package dev.iseif.listingquality.enrichment.observability;

public enum EnrichmentFeature {
  BOOK("book-enrichment"),
  SHOE_COLOR("shoe-color-enrichment");

  private final String tagValue;

  EnrichmentFeature(String tagValue) {
    this.tagValue = tagValue;
  }

  public String tagValue() {
    return tagValue;
  }
}
