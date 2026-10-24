package de.tiktak.kmm.plugin.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class MetadataResultTest {

    @Test
    void rejectsMissingTitle() {
        assertThatNullPointerException()
                .isThrownBy(() -> new MetadataResult(null, null, null, null, null))
                .withMessageContaining("title");
    }

    @Test
    void replacesMissingUniqueIdsWithEmptyMap() {
        var result = new MetadataResult("The Matrix", null, 1999, null, null);

        assertThat(result.uniqueIds()).isEmpty();
    }

    @Test
    void copiesUniqueIdsDefensively() {
        Map<String, String> ids = new HashMap<>(Map.of("tmdb", "603"));
        var result = new MetadataResult("The Matrix", "The Matrix", 1999, "Plot", ids);

        ids.clear();

        assertThat(result.uniqueIds()).containsEntry("tmdb", "603");
    }
}
