package dev.iseif.listingquality.enrichment.model.shoe;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ShoeColorProposal(
    @NotNull ShoeField field,
    @NotNull List<@NotNull ShoeColor> currentValue,
    @NotNull List<@NotNull ShoeColor> proposedValue) {

  public ShoeColorProposal {
    currentValue = currentValue == null ? List.of() : List.copyOf(currentValue);
    proposedValue = proposedValue == null ? List.of() : List.copyOf(proposedValue);
  }
}
