package de.tiktak.kmm.plugin.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ScrapeRequestTest {

    @Test
    void createsRequestWithAllValues() {
        var request = new ScrapeRequest(MediaType.MOVIE, "The Matrix", 1999, Map.of("imdb", "tt0133093"));

        assertThat(request.mediaType()).isEqualTo(MediaType.MOVIE);
        assertThat(request.title()).isEqualTo("The Matrix");
        assertThat(request.year()).isEqualTo(1999);
        assertThat(request.uniqueIds()).containsEntry("imdb", "tt0133093");
    }

    @Test
    void replacesMissingUniqueIdsWithEmptyMap() {
        var request = new ScrapeRequest(MediaType.MOVIE, "The Matrix", null, null);

        assertThat(request.uniqueIds()).isEmpty();
    }

    @Test
    void copiesUniqueIdsDefensively() {
        Map<String, String> ids = new HashMap<>(Map.of("imdb", "tt0133093"));
        var request = new ScrapeRequest(MediaType.MOVIE, "The Matrix", null, ids);

        ids.put("tmdb", "603");

        assertThat(request.uniqueIds()).containsOnlyKeys("imdb");
    }

    @Test
    void rejectsMissingMediaType() {
        assertThatNullPointerException()
                .isThrownBy(() -> new ScrapeRequest(null, "The Matrix", null, null))
                .withMessageContaining("mediaType");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void rejectsBlankTitle(String title) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ScrapeRequest(MediaType.MOVIE, title, null, null))
                .withMessageContaining("title");
    }
}
