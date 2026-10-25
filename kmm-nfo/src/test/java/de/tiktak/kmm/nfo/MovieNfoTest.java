package de.tiktak.kmm.nfo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class MovieNfoTest {

    @Test
    void replacesMissingCollectionsWithEmptyOnes() {
        var nfo = new MovieNfo(null, null, null, null, null, null, null, null, null, null, null, null);

        assertThat(nfo.genres()).isEmpty();
        assertThat(nfo.uniqueIds()).isEmpty();
        assertThat(nfo.effectiveYear()).isNull();
    }

    @Test
    void normalizesUniqueIdTypesToLowerCase() {
        var nfo = new MovieNfo(null, null, null, null, null, null, null, null, null, null, null,
                Map.of("IMDB", "tt0113277"));

        assertThat(nfo.uniqueIds()).containsOnlyKeys("imdb");
        assertThat(nfo.uniqueId("Imdb")).contains("tt0113277");
        assertThat(nfo.uniqueId("tmdb")).isEmpty();
    }

    @Test
    void keepsOrderOfUniqueIds() {
        Map<String, String> ids = new LinkedHashMap<>();
        ids.put("TMDB", "949");
        ids.put("imdb", "tt0113277");
        ids.put("tvdb", "1");

        var nfo = new MovieNfo(null, null, null, null, null, null, null, null, null, null, null, ids);

        assertThat(nfo.uniqueIds().keySet()).containsExactly("tmdb", "imdb", "tvdb");
    }

    @Test
    void copiesGenresDefensively() {
        List<String> genres = new ArrayList<>(List.of("Drama"));
        var nfo = new MovieNfo(null, null, null, null, null, null, null, null, null, null, genres, null);

        genres.add("Krimi");

        assertThat(nfo.genres()).containsExactly("Drama");
    }

    @Test
    void prefersExplicitYearOverPremiered() {
        var nfo = new MovieNfo(null, null, null, 1996, LocalDate.of(1995, 12, 15), null, null, null, null, null,
                null, null);

        assertThat(nfo.effectiveYear()).isEqualTo(1996);
    }
}
