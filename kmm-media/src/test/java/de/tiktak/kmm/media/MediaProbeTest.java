package de.tiktak.kmm.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tiktak.kmm.nfo.StreamDetails;
import de.tiktak.kmm.scanner.DiscFormat;

class MediaProbeTest {

    private static final String HALLOWEEN = fixture("halloween.json");
    private static final String SPECIAL_CASES = fixture("special-cases.json");

    private final List<String> inputs = new ArrayList<>();

    @TempDir
    Path directory;

    @Test
    void translatesEmbersFormatFromARealMatroskaFile() throws IOException {
        Path movie = file("Halloween.mkv", 1000);

        StreamDetails details = probe(HALLOWEEN).probe(movie, DiscFormat.FILE);

        assertThat(details.videos()).containsExactly(new StreamDetails.Video("h264", 2.4, 1920, 800, 7289, 4852,
                "Progressive", null, null, null, null, null, 1000L));
        assertThat(details.audios()).containsExactly(new StreamDetails.Audio("dca", "deu", 6, 1536),
                new StreamDetails.Audio("dca", "eng", 6, 1536));
        assertThat(details.subtitles()).containsExactly(new StreamDetails.Subtitle("deu", false));
        assertThat(inputs).containsExactly(movie.toString());
    }

    @Test
    void handlesCoverArtHdrFourCcsDtsHdAndForcedSubtitles() throws IOException {
        Path movie = file("Avatar.mkv", 10);

        StreamDetails details = probe(SPECIAL_CASES).probe(movie, DiscFormat.FILE);

        assertThat(details.videos()).containsExactly(
                new StreamDetails.Video("hevc", 2.4, 3840, 1600, null, 24000, "Interlaced", "left_right", null,
                        "dolbyvision", null, null, 10L),
                new StreamDetails.Video("xvid", 1.778, 720, 576, null, null, null, null, null, "hlg", null, null, 10L));
        assertThat(details.audios()).extracting(StreamDetails.Audio::codec, StreamDetails.Audio::language)
                .containsExactly(tuple("dtshd_ma", "eng"),
                        tuple("dtshd_hra", "fra"),
                        tuple("truehd", null));
        assertThat(details.subtitles()).containsExactly(new StreamDetails.Subtitle("deu", true),
                new StreamDetails.Subtitle("deu", true));
    }

    @Test
    void addsUpDurationAndSizeOfAllPartsAndFindsExternalSubtitles() throws IOException {
        Path first = file("Heat cd1.avi", 100);
        file("Heat cd2.avi", 50);
        Path german = file("Heat cd1.de.forced.srt", 1);
        Path english = file("Heat cd1.eng.idx", 1);
        file("Heat cd1.eng.sub", 1);
        file("Heat cd1.nfo", 1);

        StreamDetails details = probe(HALLOWEEN).probe(first, DiscFormat.FILE);

        assertThat(details.videos().getFirst().durationSeconds()).isEqualTo(14579);
        assertThat(details.videos().getFirst().fileSize()).isEqualTo(150L);
        assertThat(details.subtitles()).containsExactly(new StreamDetails.Subtitle("deu", false),
                new StreamDetails.Subtitle("deu", true, german.toAbsolutePath().toString()),
                new StreamDetails.Subtitle("eng", false, english.toAbsolutePath().toString()));
        assertThat(inputs).hasSize(2);
    }

    @Test
    void findsSubtitlesWhoseNameContinuesWithADashUnderscoreOrSpace() throws IOException {
        Path video = file("vov-heat-720p.mkv", 100);
        Path forced = file("vov-heat-720p-eng-forced.idx", 1);
        file("vov-heat-720p-eng-forced.sub", 1);
        Path german = file("vov-heat-720p_de.srt", 1);
        Path plain = file("vov-heat-720p.idx", 1);
        file("vov-heat-720p.sub", 1);
        Path spaced = file("vov-heat-720p fr.srt", 1);
        file("vov-heat-720pX.srt", 1);
        file("vov-heat.srt", 1);

        StreamDetails details = probe(HALLOWEEN).probe(video, DiscFormat.FILE);

        assertThat(details.subtitles()).filteredOn(StreamDetails.Subtitle::isExternal).containsExactlyInAnyOrder(
                new StreamDetails.Subtitle("eng", true, forced.toAbsolutePath().toString()),
                new StreamDetails.Subtitle("deu", false, german.toAbsolutePath().toString()),
                new StreamDetails.Subtitle(null, false, plain.toAbsolutePath().toString()),
                new StreamDetails.Subtitle("fra", false, spaced.toAbsolutePath().toString()));
    }

    @Test
    void readsTheLargestTitleOfADvdInOneGo() throws IOException {
        Path ifo = file("VIDEO_TS/VIDEO_TS.IFO", 1);
        file("VIDEO_TS/VTS_01_0.VOB", 500);
        file("VIDEO_TS/VTS_01_1.VOB", 100);
        Path second = file("VIDEO_TS/VTS_02_2.VOB", 300);
        Path first = file("VIDEO_TS/VTS_02_1.VOB", 300);

        StreamDetails details = probe(HALLOWEEN).probe(ifo, DiscFormat.DVD);

        assertThat(inputs).containsExactly("concat:" + first.toAbsolutePath() + "|" + second.toAbsolutePath());
        assertThat(details.videos().getFirst().fileSize()).isEqualTo(600L);
        assertThat(details.videos().getFirst().durationSeconds()).isEqualTo(7289);
    }

    @Test
    void readsTheLargestStreamOfABluRay() throws IOException {
        Path index = file("BDMV/index.bdmv", 1);
        file("BDMV/STREAM/00001.m2ts", 10);
        Path main = file("BDMV/STREAM/00800.M2TS", 900);

        probe(HALLOWEEN).probe(index, DiscFormat.BLURAY);

        assertThat(inputs).containsExactly(main.toAbsolutePath().toString());
    }

    @Test
    void rejectsDiscsWithoutVideoAndFilesWithoutStreams() throws IOException {
        Path ifo = file("VIDEO_TS/VIDEO_TS.IFO", 1);
        Path index = file("BDMV/index.bdmv", 1);
        Path movie = file("Leer.mkv", 1);

        assertThatThrownBy(() -> probe(HALLOWEEN).probe(ifo, DiscFormat.DVD)).isInstanceOf(MediaProbeException.class);
        assertThatThrownBy(() -> probe(HALLOWEEN).probe(index, DiscFormat.BLURAY))
                .isInstanceOf(MediaProbeException.class);
        assertThatThrownBy(() -> probe("{\"streams\": []}").probe(movie, DiscFormat.FILE))
                .isInstanceOf(MediaProbeException.class).hasMessageContaining("keine erkennbaren Spuren");
        assertThatThrownBy(() -> probe("kein json").probe(movie, DiscFormat.FILE))
                .isInstanceOf(MediaProbeException.class).hasMessageContaining("unverständliche Ausgabe");
        assertThatThrownBy(() -> probe(HALLOWEEN).probe(directory.resolve("fehlt/Heat cd1.avi"), DiscFormat.FILE))
                .isInstanceOf(MediaProbeException.class);
    }

    private MediaProbe probe(String json) {
        return new MediaProbe(input -> {
            inputs.add(input);
            return json;
        });
    }

    private Path file(String relativePath, long size) throws IOException {
        Path file = directory.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[(int) size]);
        return file;
    }

    private static String fixture(String name) {
        try (InputStream stream = MediaProbeTest.class.getResourceAsStream("/ffprobe/" + name)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
