package de.tiktak.kmm.scanner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class EpisodeNameParserTest {

    private final EpisodeNameParser parser = new EpisodeNameParser();

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Alfred Hitchcock zeigt - S01E04 - Der letzte Zeuge | 1 | 4",
            "faking.hitler.s01e02.german.720p.bluray.x264-intention | 1 | 2",
            "S01E05.Alternative Fakten.conv | 1 | 5",
            "Tatort - S1970E01 - 0001 - NDR Hamburg - Trimmel 1 - Taxi nach Leipzig 720p | 1970 | 1",
            "Serie S02 E07 Titel | 2 | 7",
            "Serie - S00E03 - Special | 0 | 3",
            "Serie - 3x12 - Titel | 3 | 12",
            "tvs-got-dts-dl-7p-bd-x264-101 | 1 | 1",
            "tvs-st-dd51-dl-7p-nfuhd-x264-208-rp | 2 | 8",
            "Serie.1012.Titel | 10 | 12"
    })
    void recognizesSeasonAndEpisode(String name, int season, int episode) {
        assertThat(parser.parse(name)).contains(new EpisodeNumber(season, List.of(episode)));
    }

    @Test
    void recognizesMultiEpisodeFiles() {
        assertThat(parser.parse("Die verwegenen Vier - S01E01E02E03E04 - Die verwegenen Vier reißen aus"))
                .contains(new EpisodeNumber(1, List.of(1, 2, 3, 4)));
        assertThat(parser.parse("ALF - S01E16-E17 - Gestatten, mein Name ist Schlegel"))
                .contains(new EpisodeNumber(1, List.of(16, 17)));
        assertThat(parser.parse("Serie 2x03x04")).contains(new EpisodeNumber(2, List.of(3, 4)));
        assertThat(parser.parse("Serie S01E02E02")).contains(new EpisodeNumber(1, List.of(2)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"So4E46E47E48", "Der Film (1986)", "Serie 1986 Titel", "Video 1920x1080", "x264-720p",
            "Garfield"})
    void ignoresNamesWithoutEpisode(String name) {
        assertThat(parser.parse(name)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "ALF - S01E02 - Der Fremde | Der Fremde",
            "S01E05.Alternative Fakten.conv | Alternative Fakten.conv",
            "Serie 3x12_Titel | Titel"
    })
    void suggestsTheTextAfterTheEpisodeAsTitle(String name, String title) {
        assertThat(parser.titleAfterNumber(name)).contains(title);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Das zehnte Königreich - S01E01E02", "tvs-got-dts-dl-7p-bd-x264-101", "Garfield"})
    void suggestsNoTitleWithoutText(String name) {
        assertThat(parser.titleAfterNumber(name)).isEmpty();
    }

    @Test
    void rejectsInvalidNumbers() {
        assertThatThrownBy(() -> new EpisodeNumber(-1, List.of(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EpisodeNumber(1, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThat(new EpisodeNumber(1, List.of(3, 4)).firstEpisode()).isEqualTo(3);
    }
}
