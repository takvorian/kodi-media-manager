package de.tiktak.kmm.plugin.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class SearchResultTest {

    @Test
    void allowsMissingOptionalValues() {
        var result = new SearchResult("949", "Heat", null, null, null, null);

        assertThat(result.providerId()).isEqualTo("949");
        assertThat(result.posterUrl()).isNull();
    }

    @Test
    void rejectsMissingIdOrTitle() {
        assertThatNullPointerException().isThrownBy(() -> new SearchResult(null, "Heat", null, null, null, null));
        assertThatNullPointerException().isThrownBy(() -> new SearchResult("949", null, null, null, null, null));
    }
}
