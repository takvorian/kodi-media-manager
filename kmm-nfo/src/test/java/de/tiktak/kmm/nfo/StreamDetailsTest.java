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
            List.of(new StreamDetails.Video("h264", 2.388, 1280, 536, 5733, 1513, "Progressive", null, null)),
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

    private static MovieNfo movie(StreamDetails streams) {
        MovieNfoSupplement supplement = new MovieNfoSupplement(List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), null, null, null, null, null, streams);
        return new MovieNfo("Heat", null, null, 1995, null, null, null, null, null, null, List.of(), Map.of(),
                supplement);
    }
}
