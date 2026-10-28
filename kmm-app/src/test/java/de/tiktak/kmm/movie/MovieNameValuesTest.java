package de.tiktak.kmm.movie;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import de.tiktak.kmm.nfo.StreamDetails;

class MovieNameValuesTest {

    private static final MovieMetadata HEAT = new MovieMetadata("Heat", "Heat OT", null, 1995, null, null, null, null,
            170, "FSK 16", List.of("Krimi", "Drama"), "tt0113277", "949", "Director's Cut", "bluray");
    private static final MovieSupplement SUPPLEMENT = new MovieSupplement(List.of(), List.of("Michael Mann"),
            List.of(), List.of(), List.of("USA", "Kanada"), List.of(),
            List.of(new MovieSupplement.Rating("tmdb", 7.95, 10, 100, false),
                    new MovieSupplement.Rating("imdb", 8.3, 10, 650000, true)),
            new MovieSupplement.SetReference("Mann Collection", null), null, null, null);
    private static final StreamDetails STREAMS = new StreamDetails(
            List.of(video(1920, 800, null, null)),
            List.of(new StreamDetails.Audio("dtshd_ma", "eng", 6, null), new StreamDetails.Audio("ac3", "deu", 2, null)),
            List.of());

    private final MovieNameValues values = new MovieNameValues(HEAT, SUPPLEMENT, STREAMS);

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "title|Heat",
        "originaltitle|Heat OT",
        "sorttitle|Heat",
        "year|1995",
        "edition|Director's Cut",
        "set|Mann Collection",
        "imdb|tt0113277",
        "tmdb|949",
        "genres|Krimi, Drama",
        "directors|Michael Mann",
        "countries|USA, Kanada",
        "mpaa|FSK 16",
        "rating|8.3",
        "resolution|1080p",
        "videocodec|h264",
        "audiocodec|dtshd_ma",
        "channels|6",
        "source|bluray"
    })
    void providesTheValuesOfAMovie(String placeholder, String expected) {
        assertThat(values.value(placeholder)).isEqualTo(expected);
    }

    @Test
    void leavesMissingValuesEmpty() {
        MovieNameValues empty = new MovieNameValues(new MovieMetadata("Heat", null, "Heat, The", null, null, null, null,
                null, null, null, List.of(), null, null, null, null), MovieSupplement.EMPTY, StreamDetails.EMPTY);

        assertThat(empty.value("sorttitle")).isEqualTo("Heat, The");
        assertThat(empty.value("year")).isNull();
        assertThat(empty.value("genres")).isNull();
        assertThat(empty.value("set")).isNull();
        assertThat(empty.value("rating")).isNull();
        assertThat(empty.value("resolution")).isNull();
        assertThat(empty.value("channels")).isNull();
        assertThat(empty.value("3d")).isNull();
        assertThat(empty.value("unbekannt")).isNull();
    }

    @Test
    void takesTheFirstRatingWithoutADefault() {
        MovieSupplement supplement = new MovieSupplement(List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(new MovieSupplement.Rating("tmdb", 7.95, 10, 100, false)), null, null, null, null);

        assertThat(new MovieNameValues(HEAT, supplement, STREAMS).value("rating")).isEqualTo("8.0");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "-", value = {
        "720|576|-|-|576p",
        "854|480|-|-|480p",
        "1280|720|-|-|720p",
        "3840|1600|-|-|4K",
        "9000|5000|-|-|-",
        "0|0|-|-|-",
        "1920|1080|left_right|-|1080p"
    })
    void describesResolutionsLikeKodi(int width, int height, String stereoMode, String multiView, String expected) {
        StreamDetails streams = new StreamDetails(List.of(video(width, height, stereoMode, multiView)), List.of(),
                List.of());

        assertThat(new MovieNameValues(HEAT, SUPPLEMENT, streams).value("resolution")).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "-", value = {
        "left_right|-|3d",
        "-|2|3d",
        "-|1|-",
        "''|-|-"
    })
    void marksThreeDimensionalVideos(String stereoMode, String multiView, String expected) {
        StreamDetails streams = new StreamDetails(List.of(video(1920, 1080, stereoMode, multiView)), List.of(),
                List.of());

        assertThat(new MovieNameValues(HEAT, SUPPLEMENT, streams).value("3d")).isEqualTo(expected);
    }

    private static StreamDetails.Video video(int width, int height, String stereoMode, String multiView) {
        return new StreamDetails.Video("h264", 2.4, width, height, null, null, null, stereoMode, null, null,
                multiView, null, null);
    }
}
