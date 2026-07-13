package dev.iseif.listingquality.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ListingReview(
    @JsonPropertyDescription("Overall listing quality from 0 to 100, where 100 is a complete, clear, easy-to-find listing.")
    @Min(0) @Max(100) int qualityScore,

    @JsonPropertyDescription("Important attributes a buyer would expect that are absent from the draft, e.g. \"keyboard layout\". Empty if nothing is missing.")
    @NotNull List<@NotBlank String> missingFields,

    @JsonPropertyDescription("Concrete problems with the information that is present, e.g. a description that is too vague. Empty if there are none.")
    @NotNull List<@NotBlank String> issues,

    @JsonPropertyDescription("Actionable improvements the seller could make to raise the quality. Empty if none apply.")
    @NotNull List<@NotBlank String> suggestions,

    @JsonPropertyDescription("True when the listing may be unsafe, misleading, illegal, or too incomplete to assess and a human should review it.")
    boolean requiresHumanReview
) {
}
