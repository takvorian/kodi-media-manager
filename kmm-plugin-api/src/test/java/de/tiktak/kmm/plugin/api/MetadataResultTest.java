package de.tiktak.kmm.plugin.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class MetadataResultTest {

    @Test
    void rejectsMissingTitle() {
        assertThatNullPointerException()
                .isThrownBy(() -> MetadataResult.builder().build())
                .withMessageContaining("title");
    }

    @Test
    void replacesMissingCollectionsWithEmptyOnes() {
        var result = MetadataResult.builder().title("The Matrix").year(1999).build();

        assertThat(result.uniqueIds()).isEmpty();
        assertThat(result.genres()).isEmpty();
    }

    @Test
    void buildsResultWithAllValues() {
        var result = MetadataResult.builder()
                .title("Heat").originalTitle("Heat").year(1995).premiered(LocalDate.of(1995, 12, 15))
                .plot("Handlung").tagline("A Los Angeles Crime Saga").runtime(170).certification("FSK 16")
                .genres(List.of("Krimi")).uniqueIds(Map.of("tmdb", "949"))
                .build();

        assertThat(result.runtime()).isEqualTo(170);
        assertThat(result.certification()).isEqualTo("FSK 16");
        assertThat(result.genres()).containsExactly("Krimi");
    }

    @Test
    void copiesCollectionsDefensively() {
        Map<String, String> ids = new HashMap<>(Map.of("tmdb", "603"));
        List<String> genres = new ArrayList<>(List.of("Action"));
        var result = MetadataResult.builder().title("The Matrix").genres(genres).uniqueIds(ids).build();

        ids.clear();
        genres.clear();

        assertThat(result.uniqueIds()).containsEntry("tmdb", "603");
        assertThat(result.genres()).containsExactly("Action");
    }
}
