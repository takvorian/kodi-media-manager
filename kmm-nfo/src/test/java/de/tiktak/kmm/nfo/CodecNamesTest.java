package de.tiktak.kmm.nfo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CodecNamesTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "avc1|h264", "AVC|h264", "V_MPEG4/ISO/AVC|h264", "hvc1|hevc", "av01|av1", "xvid|xvid", "dx50|dx50",
        "mpeg2video|mpeg2video"
    })
    void translatesVideoCodecsToKodiNames(String raw, String kodi) {
        assertThat(CodecNames.video(raw)).isEqualTo(kodi);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "mp4a-40-2|aac", "mp4a-a9|dca", "00002000-0000-0010-8000-00aa00389b71|ac3", "dts|dca", "ma / core|dtshd_ma",
        "x / ma / core|dtshd_ma_x", "dca|dca", "truehd|truehd", "E-AC-3+Atmos|eac3_atmos"
    })
    void translatesAudioCodecsToKodiNames(String raw, String kodi) {
        assertThat(CodecNames.audio(raw)).isEqualTo(kodi);
    }

    @Test
    void normalizesAllTracksAndKeepsDetailsThatNeedNothing() {
        StreamDetails raw = new StreamDetails(
                List.of(new StreamDetails.Video("avc1", 1.78, 1920, 1080, 6000, null, null, null, "deu")),
                List.of(new StreamDetails.Audio("mp4a-40-2", "deu", 2, 128)),
                List.of(new StreamDetails.Subtitle("deu", false)));

        StreamDetails normalized = CodecNames.normalize(raw);

        assertThat(normalized.videos().getFirst().codec()).isEqualTo("h264");
        assertThat(normalized.videos().getFirst().width()).isEqualTo(1920);
        assertThat(normalized.audios().getFirst()).isEqualTo(new StreamDetails.Audio("aac", "deu", 2, 128));
        assertThat(normalized.subtitles()).isEqualTo(raw.subtitles());
        assertThat(CodecNames.normalize(normalized)).isSameAs(normalized);
        assertThat(CodecNames.normalize(null)).isNull();
        assertThat(CodecNames.audio(null)).isNull();
    }
}
