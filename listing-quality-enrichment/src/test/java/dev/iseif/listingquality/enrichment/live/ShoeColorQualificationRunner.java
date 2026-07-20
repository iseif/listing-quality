package dev.iseif.listingquality.enrichment.live;

import dev.iseif.listingquality.enrichment.model.shoe.*;
import dev.iseif.listingquality.enrichment.prompt.ShoeColorPrompt;
import dev.iseif.listingquality.enrichment.service.shoe.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Set;
import java.util.stream.Collectors;

final class ShoeColorQualificationRunner {

  private final ShoeColorPrompt prompt;

  ShoeColorQualificationRunner(ShoeColorPrompt prompt) {
    this.prompt = prompt;
  }

  ShoeColorQualificationReport run(
      ShoeColorGenerator generator,
      ShoeColorExtractionValidator validator,
      ShoeColorQualificationDataset dataset,
      int repetitions) {
    if (repetitions < 1) {
      throw new IllegalArgumentException("repetitions must be at least one");
    }
    var observations = new ArrayList<ShoeColorQualificationObservation>();
    for (ShoeColorQualificationCase testCase : dataset.cases()) {
      String renderedPrompt = prompt.render(testCase.images());
      for (int repetition = 0; repetition < repetitions; repetition++) {
        observations.add(runOnce(generator, validator, testCase, renderedPrompt));
      }
    }
    return ShoeColorQualificationReport.calculate(observations);
  }

  private ShoeColorQualificationObservation runOnce(
      ShoeColorGenerator generator,
      ShoeColorExtractionValidator validator,
      ShoeColorQualificationCase testCase,
      String renderedPrompt) {
    long startedAt = System.nanoTime();
    try {
      var generated = generator.generate(renderedPrompt, testCase.images());
      ValidatedShoeColorExtraction validated = validator.validate(generated, testCase.images());
      return observation(
          testCase,
          validated.outcome(),
          primary(validated),
          validated.observedColors().stream()
              .map(ObservedShoeColor::color)
              .collect(Collectors.toUnmodifiableSet()),
          validated.ignoredImages().stream()
              .map(IgnoredShoeImage::imageId)
              .collect(Collectors.toUnmodifiableSet()),
          false,
          null,
          startedAt);
    } catch (InvalidShoeColorResponseException exception) {
      return observation(
          testCase,
          ShoeColorOutcome.INCONCLUSIVE,
          null,
          Set.of(),
          Set.of(),
          false,
          exception.failure(),
          startedAt);
    } catch (RuntimeException _) {
      return observation(
          testCase,
          ShoeColorOutcome.INCONCLUSIVE,
          null,
          Set.of(),
          Set.of(),
          true,
          null,
          startedAt);
    }
  }

  private ShoeColor primary(ValidatedShoeColorExtraction extraction) {
    return extraction.observedColors().stream()
        .filter(color -> color.role() == ShoeColorRole.PRIMARY)
        .map(ObservedShoeColor::color)
        .findFirst()
        .orElse(null);
  }

  private ShoeColorQualificationObservation observation(
      ShoeColorQualificationCase testCase,
      ShoeColorOutcome actualOutcome,
      ShoeColor actualPrimary,
      Set<ShoeColor> actualColors,
      Set<String> actualIgnored,
      boolean schemaFailure,
      ShoeColorValidationFailure validationFailure,
      long startedAt) {
    long latency = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    return new ShoeColorQualificationObservation(
        testCase.id(),
        testCase.expectedOutcome(),
        testCase.expectedPrimary(),
        testCase.expectedColors(),
        testCase.expectedIgnoredImageIds(),
        testCase.forbiddenColors(),
        testCase.outsoleNegative(),
        testCase.backgroundPropNegative(),
        actualOutcome,
        actualPrimary,
        actualColors,
        actualIgnored,
        schemaFailure,
        validationFailure,
        latency);
  }
}
