package de.tiktak.kmm.nfo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StreamLabelsTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "-", value = {
        "720|576|576p", "854|480|480p", "960|540|540p", "1280|720|720p", "1920|1080|1080p", "3840|1600|4K",
        "7680|4320|8K", "9000|5000|-", "0|0|-", "-|720|-"
    })
    void describesResolutionsLikeKodi(Integer width, Integer height, String expected) {
        assertThat(StreamLabels.resolution(video(width, height, null, null))).isEqualTo(Optional.ofNullable(expected));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "-", value = {
        "left_right|-|3d", "-|2|3d", "-|12|3d", "-|1|-", "''|-|-", "-|-|-"
    })
    void marksThreeDimensionalVideosLikeEmber(String stereoMode, String multiView, String expected) {
        assertThat(StreamLabels.threeD(video(1920, 1080, stereoMode, multiView)))
                .isEqualTo(Optional.ofNullable(expected));
    }

    private static StreamDetails.Video video(Integer width, Integer height, String stereoMode, String multiView) {
        return new StreamDetails.Video("h264", null, width, height, null, null, null, stereoMode, null, null,
                multiView, null, null);
    }
}
