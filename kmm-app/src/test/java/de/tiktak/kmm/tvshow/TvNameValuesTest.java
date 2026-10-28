package de.tiktak.kmm.tvshow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.TreeMap;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import de.tiktak.kmm.nfo.StreamDetails;

class TvNameValuesTest {

    private static final TvShowMetadata LOST = new TvShowMetadata("Lost", "Lost OT", null, 2004, null, null, null,
            "FSK 16", null, List.of("Drama", "Mystery"), List.of(), List.of(), "73739", "4607", "tt0411008", null);
    private static final TvShowExtras EXTRAS = new TvShowExtras(List.of(),
            List.of(new TvRating("tvdb", 8.06, 10, 100, false)), new TreeMap<>());
    private static final StreamDetails STREAMS = new StreamDetails(
            List.of(new StreamDetails.Video("h264", 1.78, 1280, 720, null, null, null, null, null, null, null, null,
                    null)),
            List.of(new StreamDetails.Audio("ac3", "deu", 6, null)), List.of());

    private final TvNameValues values = new TvNameValues(LOST, EXTRAS, List.of(
            new TvNameValues.EpisodeValues(1, 1, "Pilot (1)", "2004-09-22", "dvd", STREAMS),
            new TvNameValues.EpisodeValues(1, 2, "Pilot (2)", "2004-09-29", null, StreamDetails.EMPTY)));

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "-", value = {
        "show|Lost",
        "originaltitle|Lost OT",
        "sorttitle|Lost",
        "year|2004",
        "imdb|tt0411008",
        "tmdb|4607",
        "tvdb|73739",
        "genres|Drama, Mystery",
        "mpaa|FSK 16",
        "rating|8.1",
        "season|1",
        "episode|1E2",
        "title|Pilot (1) - Pilot (2)",
        "aired|2004-09-22",
        "resolution|720p",
        "videocodec|h264",
        "audiocodec|ac3",
        "channels|6",
        "source|dvd",
        "3d|-",
        "edition|-"
    })
    void providesTheValuesOfAShowAndTheEpisodesOfAFile(String placeholder, String expected) {
        assertThat(values.value(placeholder)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "-", value = {"season|-", "episode|-", "title|-", "resolution|-",
        "channels|-", "rating|-"})
    void leavesEpisodeValuesEmptyForTheShowFolder(String placeholder, String expected) {
        TvNameValues show = new TvNameValues(LOST, TvShowExtras.EMPTY, List.of());

        assertThat(show.value(placeholder)).isEqualTo(expected);
    }
}
