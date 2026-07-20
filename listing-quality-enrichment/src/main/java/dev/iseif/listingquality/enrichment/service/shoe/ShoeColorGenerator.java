package dev.iseif.listingquality.enrichment.service.shoe;

import dev.iseif.listingquality.enrichment.media.ProductImage;
import dev.iseif.listingquality.enrichment.model.shoe.GeneratedShoeColorExtraction;

import java.util.List;

public interface ShoeColorGenerator {

  GeneratedShoeColorExtraction generate(String prompt, List<ProductImage> images);
}
