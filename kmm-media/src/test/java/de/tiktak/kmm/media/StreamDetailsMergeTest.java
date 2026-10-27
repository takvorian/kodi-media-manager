package de.tiktak.kmm.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.tiktak.kmm.nfo.StreamDetails;

class StreamDetailsMergeTest {

    @Test
    void takesTheProbedTracksButKeepsLanguagesAndThreeDInformation() {
        StreamDetails existing = new StreamDetails(
                List.of(new StreamDetails.Video("avc1", 2.4, 1920, 800, 100, 1, "Progressive", "left_right", "deu",
                        null, "2", "left_right", 1L)),
                List.of(new StreamDetails.Audio("dca", "deu", 6, 1509), new StreamDetails.Audio("ac3", "eng", 2, 192)),
                List.of(new StreamDetails.Subtitle("deu", false), new StreamDetails.Subtitle("eng", false, "/a.srt")));
        StreamDetails probed = new StreamDetails(
                List.of(new StreamDetails.Video("h264", 2.4, 1920, 800, 7289, 4852, null, null, null, "hdr10",
                        null, null, 7L)),
                List.of(new StreamDetails.Audio("dca", null, 6, 1536), new StreamDetails.Audio("ac3", "fra", 2, 192),
                        new StreamDetails.Audio("aac", null, 2, 128)),
                List.of(new StreamDetails.Subtitle("fra", false, "/b.fr.srt"), new StreamDetails.Subtitle(null, true)));

        StreamDetails merged = StreamDetailsMerge.keepKnown(probed, existing);

        assertThat(merged.videos()).containsExactly(new StreamDetails.Video("h264", 2.4, 1920, 800, 7289, 4852,
                "Progressive", "left_right", "deu", "hdr10", "2", "left_right", 7L));
        assertThat(merged.audios()).containsExactly(new StreamDetails.Audio("dca", "deu", 6, 1536),
                new StreamDetails.Audio("ac3", "fra", 2, 192), new StreamDetails.Audio("aac", null, 2, 128));
        assertThat(merged.subtitles()).containsExactly(new StreamDetails.Subtitle("deu", true),
                new StreamDetails.Subtitle("fra", false, "/b.fr.srt"));
    }

    @Test
    void takesEverythingProbedWithoutExistingDetails() {
        StreamDetails probed = new StreamDetails(List.of(), List.of(new StreamDetails.Audio("aac", null, 2, 128)),
                List.of());

        assertThat(StreamDetailsMerge.keepKnown(probed, StreamDetails.EMPTY)).isEqualTo(probed);
    }
}
