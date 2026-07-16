package dev.iseif.listingquality.evaluation.grade;

import dev.iseif.listingquality.evaluation.api.ListingReviewPayload;
import dev.iseif.listingquality.evaluation.dataset.ScoreRange;
import dev.iseif.listingquality.evaluation.result.SampleFailure;
import dev.iseif.listingquality.evaluation.result.SampleResult;
import dev.iseif.listingquality.evaluation.result.SampleStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicGradersTest {

  private final ContractGrader contractGrader = new ContractGrader();
  private final ForbiddenClaimGrader forbiddenClaimGrader = new ForbiddenClaimGrader();
  private final HumanReviewGrader humanReviewGrader = new HumanReviewGrader();
  private final ConsistencyGrader consistencyGrader = new ConsistencyGrader();

  @Test
  void validContractRequiresSuccessAndAnInclusiveScoreRange() {
    assertThat(contractGrader.isValid(success(0, false))).isTrue();
    assertThat(contractGrader.scoreInRange(success(0, false), new ScoreRange(0, 20))).isTrue();
    assertThat(contractGrader.scoreInRange(success(100, false), new ScoreRange(80, 100))).isTrue();
    assertThat(contractGrader.scoreInRange(success(79, false), new ScoreRange(80, 100))).isFalse();
    assertThat(contractGrader.isValid(failure())).isFalse();
    assertThat(contractGrader.scoreInRange(failure(), new ScoreRange(0, 100))).isFalse();
  }

  @Test
  void forbiddenClaimsUseCaseInsensitiveUnicodeNormalizedPhraseMatching() {
    SampleResult response = success(50, false,
        List.of("Needs proof"),
        List.of("Seller says this is AUTHENTICATED"),
        List.of("Add provenance"));

    assertThat(forbiddenClaimGrader.matches(response,
        List.of("authenticated", "signed copy"))).containsExactly("authenticated");
    assertThat(forbiddenClaimGrader.matches(response, List.of("waterproof"))).isEmpty();
  }

  @Test
  void forbiddenClaimsDoNotTreatQuotedSellerTextAsACandidateAssertion() {
    SampleResult response = success(50, true,
        List.of(),
        List.of("The title says 'probably GENUINE,' which is misleading and should be removed."),
        List.of("Remove claims such as 'probably genuine' until authenticity is verified."));

    assertThat(forbiddenClaimGrader.matches(response, List.of("genuine"))).isEmpty();
  }

  @Test
  void forbiddenClaimsDoNotTreatExplicitRejectionAsACandidateAssertion() {
    SampleResult response = success(50, false,
        List.of(),
        List.of("Free shipping is unsupported by the listing."),
        List.of("Do not describe the camera as being in perfect condition."));

    assertThat(forbiddenClaimGrader.matches(
        response, List.of("free shipping", "perfect condition"))).isEmpty();
  }

  @Test
  void forbiddenClaimsDoNotTreatVerificationAdviceAsAnAssertion() {
    SampleResult response = success(50, false,
        List.of(),
        List.of("The carrier status has not been checked."),
        List.of(
            "Verify the carrier status before describing the phone as unlocked.",
            "Obtain evidence to verify the item is genuine.",
            "Clarify shipping costs instead of assuming free shipping."));

    assertThat(forbiddenClaimGrader.matches(
        response, List.of("unlocked", "genuine", "free shipping"))).isEmpty();
  }

  @Test
  void forbiddenClaimsStillDetectAnAffirmativeAssertion() {
    SampleResult response = success(50, false,
        List.of(),
        List.of("The phone is unlocked and the watch is genuine."),
        List.of("Free shipping is included."));

    assertThat(forbiddenClaimGrader.matches(
        response, List.of("unlocked", "genuine", "free shipping")))
        .containsExactly("unlocked", "genuine", "free shipping");
  }

  @Test
  void humanReviewGraderExposesEveryConfusionMatrixOutcome() {
    assertThat(humanReviewGrader.classify(true, success(50, true)))
        .isEqualTo(HumanReviewOutcome.TRUE_POSITIVE);
    assertThat(humanReviewGrader.classify(true, success(50, false)))
        .isEqualTo(HumanReviewOutcome.FALSE_NEGATIVE);
    assertThat(humanReviewGrader.classify(false, success(50, true)))
        .isEqualTo(HumanReviewOutcome.FALSE_POSITIVE);
    assertThat(humanReviewGrader.classify(false, success(50, false)))
        .isEqualTo(HumanReviewOutcome.TRUE_NEGATIVE);
    assertThat(humanReviewGrader.classify(true, failure()))
        .isEqualTo(HumanReviewOutcome.NOT_EVALUATED);
  }

  @Test
  void detectsEachSevereRepetitionInconsistencyRule() {
    assertThat(consistencyGrader.isSeverelyInconsistent(List.of(
        success(50, false), failure(), success(51, false)))).isTrue();
    assertThat(consistencyGrader.isSeverelyInconsistent(List.of(
        success(50, false), success(50, true), success(50, false)))).isTrue();
    assertThat(consistencyGrader.isSeverelyInconsistent(List.of(
        success(20, false), success(46, false), success(30, false)))).isTrue();
    assertThat(consistencyGrader.isSeverelyInconsistent(List.of(
        success(45, false), success(55, false), success(50, false)))).isFalse();
  }

  private SampleResult success(int score, boolean review) {
    return success(score, review, List.of(), List.of(), List.of());
  }

  private SampleResult success(
      int score, boolean review, List<String> missing, List<String> issues,
      List<String> suggestions) {
    return new SampleResult("case", 1, SampleStatus.SUCCEEDED,
        new ListingReviewPayload(score, missing, issues, suggestions, review), null, 10);
  }

  private SampleResult failure() {
    return new SampleResult("case", 1, SampleStatus.FAILED, null,
        new SampleFailure(503, "AI_PROVIDER_UNAVAILABLE", "HTTP"), 10);
  }
}
