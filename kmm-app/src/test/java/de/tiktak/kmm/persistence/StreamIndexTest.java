package de.tiktak.kmm.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import de.tiktak.kmm.nfo.StreamDetails;

class StreamIndexTest {

    @Test
    void summarizesTheFirstVideoAndAllAudioAndSubtitleTracks() {
        StreamDetails details = new StreamDetails(
                List.of(new StreamDetails.Video("HEVC", null, 3840, 1600, null, null, null, null, null, "HDR10", null,
                        null, null)),
                List.of(new StreamDetails.Audio("TrueHD", "eng", 8, null), new StreamDetails.Audio("ac3", "deu", 6, null),
                        new StreamDetails.Audio("ac3", null, 2, null)),
                List.of(new StreamDetails.Subtitle("ger", false), new StreamDetails.Subtitle(" ", true)));

        StreamIndex index = StreamIndex.of(details);

        assertThat(index.getVideoResolution()).isEqualTo(2160);
        assertThat(index.getVideoCodec()).isEqualTo("hevc");
        assertThat(index.getHdrType()).isEqualTo("hdr10");
        assertThat(index.getAudioCodecs()).isEqualTo(" truehd ac3 ");
        assertThat(index.getAudioLanguages()).isEqualTo(" eng deu ");
        assertThat(index.getSubtitleLanguages()).isEqualTo(" ger ");
        assertThat(StreamIndex.tokensOf(index.getAudioCodecs())).containsExactly("truehd", "ac3");
    }

    @Test
    void marksKnownDetailsWithoutAudioAndHasNoIndexWithoutDetails() {
        StreamIndex videoOnly = StreamIndex.of(new StreamDetails(
                List.of(new StreamDetails.Video("h264", null, null, null, null, null, null, null, null)), List.of(),
                List.of()));

        assertThat(videoOnly.getAudioCodecs()).isEqualTo(" ");
        assertThat(videoOnly.getVideoResolution()).isNull();
        assertThat(StreamIndex.of(null)).isNull();
        assertThat(StreamIndex.of(StreamDetails.EMPTY)).isNull();
        assertThat(StreamIndex.tokensOf(null)).isEmpty();
    }

    @Test
    void keepsListsWithinTheColumnLength() {
        List<StreamDetails.Audio> audios = IntStream.range(0, 200)
                .mapToObj(number -> new StreamDetails.Audio("codec" + number, null, null, null)).toList();

        StreamIndex index = StreamIndex.of(new StreamDetails(List.of(), audios, List.of()));

        assertThat(index.getAudioCodecs()).hasSizeLessThanOrEqualTo(StreamIndex.MAX_LIST_LENGTH).startsWith(" codec0 ")
                .endsWith(" ");
        assertThat(StreamIndex.containsToken("Codec 1")).isEqualTo("% codec!_1 %");
        assertThat(StreamIndex.escapeLike("50%!")).isEqualTo("50!%!!");
    }
}
