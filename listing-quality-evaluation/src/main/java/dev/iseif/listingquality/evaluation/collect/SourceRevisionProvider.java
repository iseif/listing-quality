package dev.iseif.listingquality.evaluation.collect;

import dev.iseif.listingquality.evaluation.result.SourceRevision;

@FunctionalInterface
public interface SourceRevisionProvider {
  SourceRevision capture();
}
