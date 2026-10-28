package de.tiktak.kmm.nfo;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StreamDetailsTest {

    private static final StreamDetails HEAT = new StreamDetails(
            List.of(new StreamDetails.Video("h264", 2.388, 1280, 536, 5733, 1513, "Progressive", null, null, null, null,
                    null, 2167309437L)),
            List.of(new StreamDetails.Audio("dca", "deu", 6, 1509), new StreamDetails.Audio("ac3", "eng", 2, null)),
            List.of(new StreamDetails.Subtitle("deu", true)));

    private final NfoReader reader = new NfoReader();
    private final NfoWriter writer = new NfoWriter();

    @TempDir
    Path directory;

    @Test
    void readsEmberStyleStreamDetails() throws IOException {
        Path nfo = Files.writeString(directory.resolve("Heat.nfo"), """
                <movie><title>Heat</title><fileinfo><streamdetails>
                  <audio><bitrate>1509</bitrate><channels>6</channels><codec>dca</codec><language>deu</language>
                    <longlanguage>German</longlanguage></audio>
                  <audio><channels>2</channels><codec>ac3</codec><language>eng</language></audio>
                  <subtitle><language>deu</language><forced>true</forced><type>Embedded</type></subtitle>
                  <video><aspect>2,388</aspect><bitrate>1513</bitrate><codec>h264</codec>
                    <durationinseconds>5733</durationinseconds><height>536</height><scantype>Progressive</scantype>
                    <width>1280</width><filesize>2167309437</filesize></video>
                </streamdetails></fileinfo></movie>""");

        assertThat(reader.read(nfo).supplement().streams()).isEqualTo(HEAT);
    }

    @Test
    void writesKnownStreamDetailsAndReplacesOldOnes() throws IOException {
        Path nfo = Files.writeString(directory.resolve("Heat.nfo"),
                "<movie><title>Heat</title><fileinfo><streamdetails><video><codec>mpeg2</codec></video>"
                        + "</streamdetails></fileinfo><trailer>x</trailer></movie>");

        writer.write(nfo, movie(HEAT));

        String content = Files.readString(nfo);
        assertThat(content).contains("<codec>h264</codec>", "<forced>true</forced>", "<trailer>x</trailer>")
                .doesNotContain("mpeg2");
        assertThat(content.split("<fileinfo>", -1)).hasSize(2);
        assertThat(reader.read(nfo).supplement().streams()).isEqualTo(HEAT);
    }

    @Test
    void keepsTheFileInfoWithoutKnownStreams() throws IOException {
        Path nfo = Files.writeString(directory.resolve("Heat.nfo"),
                "<movie><title>Heat</title><fileinfo><streamdetails><video><codec>mpeg2</codec></video>"
                        + "</streamdetails></fileinfo></movie>");

        writer.write(nfo, movie(StreamDetails.EMPTY));

        assertThat(Files.readString(nfo)).contains("<codec>mpeg2</codec>");
    }

    @Test
    void appendsStreamDetailsToANewNfo() throws IOException {
        Path nfo = directory.resolve("Neu.nfo");

        writer.write(nfo, movie(new StreamDetails(null, List.of(new StreamDetails.Audio("aac", null, null, null)),
                null)));

        assertThat(reader.read(nfo).supplement().streams().audios())
                .containsExactly(new StreamDetails.Audio("aac", null, null, null));
        assertThat(StreamDetails.EMPTY.isEmpty()).isTrue();
    }

    @Test
    void writesInEmbersOrderWithLongLanguageAndSubtitleType() throws IOException {
        Path nfo = directory.resolve("Heat.nfo");
        StreamDetails details = new StreamDetails(
                List.of(new StreamDetails.Video("hevc", 1.7777, 3840, 2160, 7200, null, null, null, null, "hdr10", null,
                        null, 5L)),
                List.of(new StreamDetails.Audio("truehd", "deu", 8, null)),
                List.of(new StreamDetails.Subtitle("eng", false), new StreamDetails.Subtitle("deu", true, "/x/Heat.de.srt")));

        writer.write(nfo, movie(details));

        String content = Files.readString(nfo).replaceAll("\\s+", "");
        assertThat(content).contains("<streamdetails><audio><channels>8</channels><codec>truehd</codec>"
                + "<language>deu</language><longlanguage>German</longlanguage></audio>"
                + "<subtitle><language>eng</language><longlanguage>English</longlanguage><forced>false</forced>"
                + "<type>Embedded</type></subtitle>"
                + "<subtitle><language>deu</language><longlanguage>German</longlanguage><forced>true</forced>"
                + "<path>/x/Heat.de.srt</path><type>External</type></subtitle>"
                + "<video><aspect>1.778</aspect><codec>hevc</codec><durationinseconds>7200</durationinseconds>"
                + "<hdrtype>hdr10</hdrtype><height>2160</height><width>3840</width><filesize>5</filesize></video>");
        assertThat(reader.read(nfo).supplement().streams().subtitles()).extracting(StreamDetails.Subtitle::isExternal)
                .containsExactly(false, true);
    }

    @Test
    void readsEmbersExternalSubtitlesAndDecimalFileSize() throws IOException {
        Path nfo = Files.writeString(directory.resolve("Heat.nfo"), """
                <movie><fileinfo><streamdetails>
                  <subtitle><language>deu</language><type>External</type></subtitle>
                  <video><multiview_count>2</multiview_count><multiview_layout>x</multiview_layout>
                    <filesize>12,6</filesize></video>
                </streamdetails></fileinfo></movie>""");

        StreamDetails streams = reader.read(nfo).supplement().streams();

        assertThat(streams.subtitles()).containsExactly(new StreamDetails.Subtitle("deu", false, ""));
        assertThat(streams.videos().getFirst().multiViewCount()).isEqualTo("2");
        assertThat(streams.videos().getFirst().multiViewLayout()).isEqualTo("x");
        assertThat(streams.videos().getFirst().fileSize()).isEqualTo(13L);
    }

    private static MovieNfo movie(StreamDetails streams) {
        MovieNfoSupplement supplement = new MovieNfoSupplement(List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), null, null, null, null, null, streams);
        return new MovieNfo("Heat", null, null, 1995, null, null, null, null, null, null, null, null, List.of(), Map.of(),
                supplement);
    }
}
