package dev.iseif.listingquality.enrichment.catalog.book.google;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record GoogleBooksResponse(List<Volume> items) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Volume(String id, String selfLink, VolumeInfo volumeInfo) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record VolumeInfo(
      String title,
      String subtitle,
      List<String> authors,
      String publisher,
      String publishedDate,
      String description,
      List<IndustryIdentifier> industryIdentifiers,
      Integer pageCount,
      List<String> categories,
      String language) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record IndustryIdentifier(String type, String identifier) {
  }
}
