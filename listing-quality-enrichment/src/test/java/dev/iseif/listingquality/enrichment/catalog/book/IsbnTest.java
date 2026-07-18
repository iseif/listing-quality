package dev.iseif.listingquality.enrichment.catalog.book;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IsbnTest {

  @Test
  void normalizesValidIsbnTenAndThirteenValues() {
    assertThat(Isbn.normalize("978-0-13-235088-4")).contains("9780132350884");
    assertThat(Isbn.normalize("0-13-235088-2")).contains("0132350882");
  }

  @Test
  void acceptsXAsAnIsbnTenCheckDigit() {
    assertThat(Isbn.normalize("0-8044-2957-X")).contains("080442957X");
  }

  @Test
  void rejectsInvalidChecksumsAndNonIsbnText() {
    assertThat(Isbn.normalize("9780132350885")).isEmpty();
    assertThat(Isbn.normalize("0132350883")).isEmpty();
    assertThat(Isbn.normalize("not-an-isbn")).isEmpty();
    assertThat(Isbn.normalize(null)).isEmpty();
  }
}
