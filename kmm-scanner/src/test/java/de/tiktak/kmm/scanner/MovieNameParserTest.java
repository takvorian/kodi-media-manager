package de.tiktak.kmm.scanner;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class MovieNameParserTest {

    private final MovieNameParser parser = new MovieNameParser();

    @ParameterizedTest(name = "{0} -> {1} ({2})")
    @CsvSource(delimiter = '|', nullValues = "null", value = {
            "Heat (1995)                                   | Heat                         | 1995",
            "Heat [1995]                                   | Heat                         | 1995",
            "The.Matrix.1999.1080p.BluRay.x264-GROUP       | The Matrix                   | 1999",
            "The_Matrix_Reloaded_2003                      | The Matrix Reloaded          | 2003",
            "Blade Runner 2049 (2017)                      | Blade Runner 2049            | 2017",
            "1917 (2019)                                   | 1917                         | 2019",
            "2001 A Space Odyssey (1968)                   | 2001 A Space Odyssey         | 1968",
            "Die Hard - Jetzt erst recht (1995)            | Die Hard - Jetzt erst recht  | 1995",
            "Amélie (2001)                                 | Amélie                       | 2001",
            "Heat                                          | Heat                         | null",
            "1917                                          | 1917                         | null",
            "Inception.German.DL.1080p.BluRay.x264         | Inception                    | null",
            "Avatar.2160p.UHD.BluRay.REMUX.HDR             | Avatar                       | null",
            "  Spaces   everywhere   (2010)  .             | Spaces everywhere            | 2010"
    })
    void parsesTitleAndYear(String name, String expectedTitle, Integer expectedYear) {
        ParsedMovieName parsed = parser.parse(name);

        assertThat(parsed.title()).isEqualTo(expectedTitle);
        assertThat(parsed.year()).isEqualTo(expectedYear);
        assertThat(parsed.hasTitle()).isTrue();
    }

    @ParameterizedTest(name = "{0} -> {1} ({2}) {3}")
    @CsvSource(delimiter = '|', nullValues = "null", value = {
            "Wonka (2023) tt6166392                 | Wonka       | 2023 | tt6166392",
            "Wonka tt6166392 (2023)                 | Wonka       | 2023 | tt6166392",
            "tt6166392 Wonka (2023)                 | Wonka       | 2023 | tt6166392",
            "Wonka tt6166392                        | Wonka       | null | tt6166392",
            "Wonka - TT6166392                      | Wonka       | null | tt6166392",
            "Wonka.2023.tt6166392.1080p             | Wonka       | 2023 | tt6166392",
            "Wonka (2023) [tt6166392]               | Wonka       | 2023 | tt6166392",
            "Wonka (2023) {imdb-tt6166392}          | Wonka       | 2023 | tt6166392",
            "Wonka [imdbid-tt6166392]               | Wonka       | null | tt6166392",
            "Show tt20231234                        | Show        | null | tt20231234",
            "Wonka (2023) tt123                     | Wonka       | 2023 | null",
            "Mutt1234567 (2023)                     | Mutt1234567 | 2023 | null"
    })
    void readsTheImdbIdAndKeepsItOutOfTheTitle(String name, String expectedTitle, Integer expectedYear,
            String expectedImdbId) {
        ParsedMovieName parsed = parser.parse(name);

        assertThat(parsed.title()).isEqualTo(expectedTitle);
        assertThat(parsed.year()).isEqualTo(expectedYear);
        assertThat(parsed.imdbId()).isEqualTo(expectedImdbId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"(2010)", "1080p.BluRay", "", "tt6166392"})
    void reportsMissingTitle(String name) {
        assertThat(parser.parse(name).hasTitle()).isFalse();
    }
}
