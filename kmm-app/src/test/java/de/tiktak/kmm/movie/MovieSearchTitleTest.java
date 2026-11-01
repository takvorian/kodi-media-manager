package de.tiktak.kmm.movie;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MovieSearchTitleTest {

    @ParameterizedTest(name = "{0} ({1}) -> {2}")
    @CsvSource(delimiter = '|', nullValues = "null", value = {
            "Wonka (2023) tt6166392  | 2023 | Wonka",
            "Wonka tt6166392         | null | Wonka",
            "Heat (1995)             | null | Heat",
            "Heat                    | 1995 | Heat",
            "Blade Runner 2049       | 2017 | Blade Runner 2049",
            "1917                    | 2019 | 1917"
    })
    void leavesOnlyTheActualTitle(String title, Integer year, String expected) {
        assertThat(MovieSearchTitle.of(metadata(title, year, null))).isEqualTo(expected);
    }

    @Test
    void keepsTheTitleOfAnIdentifiedMovie() {
        assertThat(MovieSearchTitle.of(metadata("Rocky (1976)", 1976, "1366"))).isEqualTo("Rocky (1976)");
    }

    @Test
    void takesTheYearFromTheTitleIfItIsMissing() {
        assertThat(MovieSearchTitle.yearOf(metadata("Heat (1995)", null, null))).isEqualTo(1995);
        assertThat(MovieSearchTitle.yearOf(metadata("Heat (1995)", 1996, null))).isEqualTo(1996);
        assertThat(MovieSearchTitle.yearOf(metadata("Heat", null, null))).isNull();
    }

    private static MovieMetadata metadata(String title, Integer year, String tmdbId) {
        return new MovieMetadata(title, null, null, year, null, null, null, null, null, null, List.of(), null, tmdbId,
                null, null);
    }
}
