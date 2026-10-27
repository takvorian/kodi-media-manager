package de.tiktak.kmm.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.tiktak.kmm.migration.ember.library.EmberStreams;
import de.tiktak.kmm.nfo.StreamDetails;

class EmberStreamMapperTest {

    private static final Path VIDEO = Path.of("/filme/Heat/Heat.mkv").toAbsolutePath();

    @Test
    void mapsAllTracksAndPutsExternalSubtitlesNextToTheVideo() {
        EmberStreams streams = new EmberStreams(
                List.of(new EmberStreams.Video("avc1", 1920, 800, "2.40", 10200, 8000, "progressive", "left_right",
                        "deu", "2", "left_right", 2167309437L)),
                List.of(new EmberStreams.Audio("DTS", "deu", 6, 1509)),
                List.of(new EmberStreams.Subtitle("deu", true, false, null),
                        new EmberStreams.Subtitle("eng", false, true, "D:\\Filme\\Heat\\Heat.en.srt"),
                        new EmberStreams.Subtitle("fra", false, true, null)));

        StreamDetails details = EmberStreamMapper.toStreamDetails(streams, VIDEO.toString());

        assertThat(details.videos()).containsExactly(new StreamDetails.Video("avc1", 2.40, 1920, 800, 10200, 8000,
                "progressive", "left_right", "deu", null, "2", "left_right", 2167309437L));
        assertThat(details.audios()).containsExactly(new StreamDetails.Audio("DTS", "deu", 6, 1509));
        assertThat(details.subtitles()).containsExactly(new StreamDetails.Subtitle("deu", true),
                new StreamDetails.Subtitle("eng", false, VIDEO.resolveSibling("Heat.en.srt").toString()),
                new StreamDetails.Subtitle("fra", false, ""));
    }

    @Test
    void ignoresUnusableAspectRatios() {
        assertThat(aspectOf(null)).isNull();
        assertThat(aspectOf("breit")).isNull();
        assertThat(aspectOf("0")).isNull();
        assertThat(aspectOf("1,778")).isEqualTo(1.778);
    }

    @Test
    void dropsLanguagesThatAreNoIsoCodes() {
        EmberStreams streams = new EmberStreams(
                List.of(new EmberStreams.Video("h264", null, null, null, null, null, null, null, "Unbekannt", null, null, null)),
                List.of(new EmberStreams.Audio("ac3", "Unbekannt", 2, null)),
                List.of(new EmberStreams.Subtitle("German", false, false, null)));

        StreamDetails details = EmberStreamMapper.toStreamDetails(streams, VIDEO.toString());

        assertThat(details.videos().getFirst().language()).isNull();
        assertThat(details.audios().getFirst().language()).isNull();
        assertThat(details.subtitles().getFirst().language()).isNull();
    }

    @Test
    void mapsNoTracksToEmptyDetails() {
        assertThat(EmberStreamMapper.toStreamDetails(EmberStreams.NONE, null).isEmpty()).isTrue();
    }

    private static Double aspectOf(String aspect) {
        EmberStreams streams = new EmberStreams(
                List.of(new EmberStreams.Video(null, null, null, aspect, null, null, null, null, null, null, null, null)), null, null);
        return EmberStreamMapper.toStreamDetails(streams, null).videos().getFirst().aspect();
    }
}
