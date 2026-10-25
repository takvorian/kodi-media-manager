package de.tiktak.kmm.plugin.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SearchQueryTest {

    @Test
    void trimsTitle() {
        assertThat(new SearchQuery(MediaType.MOVIE, "  Heat ", 1995).title()).isEqualTo("Heat");
    }

    @Test
    void rejectsMissingMediaType() {
        assertThatNullPointerException().isThrownBy(() -> new SearchQuery(null, "Heat", null));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void rejectsBlankTitle(String title) {
        assertThatIllegalArgumentException().isThrownBy(() -> new SearchQuery(MediaType.MOVIE, title, null));
    }
}
