package dev.iseif.listingquality.enrichment.live;

import dev.iseif.listingquality.enrichment.media.ProductImage;
import dev.iseif.listingquality.enrichment.model.shoe.ShoeColor;
import dev.iseif.listingquality.enrichment.model.shoe.ShoeColorOutcome;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.MimeTypeUtils;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

record ShoeColorQualificationDataset(List<ShoeColorQualificationCase> cases) {

  ShoeColorQualificationDataset {
    cases = List.copyOf(cases);
  }

  static ShoeColorQualificationDataset load(ObjectMapper objectMapper, Resource manifest) {
    try (var input = manifest.getInputStream()) {
      Manifest definition = objectMapper.readValue(input, Manifest.class);
      return new ShoeColorQualificationDataset(
          definition.cases().stream().map(ShoeColorQualificationDataset::loadCase).toList());
    } catch (IOException exception) {
      throw new IllegalStateException("Could not load shoe color qualification dataset", exception);
    }
  }

  private static ShoeColorQualificationCase loadCase(CaseDefinition definition) {
    List<ProductImage> images = IntStream.range(0, definition.resources().size())
        .mapToObj(index -> loadImage(definition.resources().get(index), index + 1))
        .toList();
    Set<ShoeColor> colors = new LinkedHashSet<>();
    if (definition.expectedPrimary() != null) {
      colors.add(definition.expectedPrimary());
    }
    colors.addAll(definition.expectedAdditional());
    return new ShoeColorQualificationCase(
        definition.id(),
        images,
        definition.expectedOutcome(),
        definition.expectedPrimary(),
        colors,
        Set.copyOf(definition.expectedIgnoredImageIds()),
        Set.copyOf(definition.forbiddenColors()),
        definition.outsoleNegative(),
        definition.backgroundPropNegative());
  }

  private static ProductImage loadImage(String path, int ordinal) {
    try {
      return new ProductImage(
          "IMAGE_" + ordinal,
          MimeTypeUtils.IMAGE_JPEG,
          new ClassPathResource(path).getContentAsByteArray());
    } catch (IOException exception) {
      throw new IllegalStateException("Could not load qualification image", exception);
    }
  }

  private record Manifest(List<CaseDefinition> cases) {
  }

  private record CaseDefinition(
      String id,
      List<String> resources,
      ShoeColorOutcome expectedOutcome,
      ShoeColor expectedPrimary,
      List<ShoeColor> expectedAdditional,
      List<String> expectedIgnoredImageIds,
      List<ShoeColor> forbiddenColors,
      boolean outsoleNegative,
      boolean backgroundPropNegative) {
  }
}
