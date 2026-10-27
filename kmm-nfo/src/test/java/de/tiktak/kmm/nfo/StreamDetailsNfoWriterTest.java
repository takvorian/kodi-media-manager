package de.tiktak.kmm.nfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StreamDetailsNfoWriterTest {

    private static final StreamDetails HALLOWEEN = new StreamDetails(
            List.of(new StreamDetails.Video("h264", 2.4, 1920, 800, 7289, 4852, "Progressive", null, null, null, null,
                    null, 7173369831L)),
            List.of(new StreamDetails.Audio("dca", "deu", 6, 1509)),
            List.of(new StreamDetails.Subtitle("deu", false)));

    private final StreamDetailsNfoWriter writer = new StreamDetailsNfoWriter();

    @TempDir
    Path directory;

    @Test
    void replacesTheFileInfoOfAnEmberNfoInEmbersFormat() throws IOException {
        String before = String.join("\r\n",
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>",
                "<movie>",
                "  <title>Halloween</title>",
                "  <fileinfo>",
                "    <streamdetails>",
                "      <video>",
                "        <codec>mpeg4</codec>",
                "      </video>",
                "    </streamdetails>",
                "  </fileinfo>",
                "  <trailer />",
                "</movie>",
                "");
        Path nfo = givenNfo("movie.nfo", before);

        writer.writeMovie(nfo, HALLOWEEN);

        assertThat(Files.readString(nfo)).isEqualTo(String.join("\r\n",
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>",
                "<movie>",
                "  <title>Halloween</title>",
                "  <fileinfo>",
                "    <streamdetails>",
                "      <audio>",
                "        <bitrate>1509</bitrate>",
                "        <channels>6</channels>",
                "        <codec>dca</codec>",
                "        <language>deu</language>",
                "        <longlanguage>German</longlanguage>",
                "      </audio>",
                "      <subtitle>",
                "        <language>deu</language>",
                "        <longlanguage>German</longlanguage>",
                "        <forced>false</forced>",
                "        <type>Embedded</type>",
                "      </subtitle>",
                "      <video>",
                "        <aspect>2.400</aspect>",
                "        <bitrate>4852</bitrate>",
                "        <codec>h264</codec>",
                "        <durationinseconds>7289</durationinseconds>",
                "        <height>800</height>",
                "        <scantype>Progressive</scantype>",
                "        <width>1920</width>",
                "        <filesize>7173369831</filesize>",
                "      </video>",
                "    </streamdetails>",
                "  </fileinfo>",
                "  <trailer />",
                "</movie>",
                ""));
        assertThat(new NfoReader().read(nfo).supplement().streams()).isEqualTo(HALLOWEEN);
    }

    @Test
    void insertsTheFileInfoIntoTheRightEpisode() throws IOException {
        String xml = """
                <episodedetails>
                    <title>Eins</title>
                    <season>1</season>
                    <episode>1</episode>
                </episodedetails>
                <episodedetails>
                    <title>Zwei</title>
                    <season>1</season>
                    <episode>2</episode>
                </episodedetails>
                """;
        Path nfo = givenNfo("episode.nfo", xml);

        writer.writeEpisode(nfo, 1, 2, HALLOWEEN);

        String content = Files.readString(nfo);
        assertThat(content).startsWith(xml.substring(0, xml.indexOf("<title>Zwei")))
                .contains("    <episode>2</episode>\n    <fileinfo>\n        <streamdetails>\n            <audio>\n"
                        + "                <bitrate>1509</bitrate>\n");
        List<EpisodeNfo> episodes = new EpisodeNfoReader().read(nfo);
        assertThat(episodes.getFirst().streams()).isEqualTo(StreamDetails.EMPTY);
        assertThat(episodes.getLast().streams()).isEqualTo(HALLOWEEN);
    }

    @Test
    void rewritesNfosThatCannotSafelyBeEditedAsText() throws IOException {
        Path nfo = givenNfo("movie.nfo",
                "<movie><title>Heat</title><plot><![CDATA[Ende </movie> mitten im Text]]></plot></movie>");

        writer.writeMovie(nfo, HALLOWEEN);

        MovieNfo read = new NfoReader().read(nfo);
        assertThat(read.plot()).isEqualTo("Ende </movie> mitten im Text");
        assertThat(read.supplement().streams()).isEqualTo(HALLOWEEN);
    }

    @Test
    void leavesTheNfoAloneWithoutStreams() throws IOException {
        String xml = "<movie><title>Heat</title></movie>";
        Path nfo = givenNfo("movie.nfo", xml);

        writer.writeMovie(nfo, StreamDetails.EMPTY);

        assertThat(Files.readString(nfo)).isEqualTo(xml);
    }

    @Test
    void rejectsMissingNfosAndUnknownEpisodes() throws IOException {
        Path nfo = givenNfo("episode.nfo",
                "<episodedetails><title>Eins</title><season>1</season><episode>1</episode></episodedetails>");

        assertThatThrownBy(() -> writer.writeEpisode(nfo, 1, 5, HALLOWEEN)).isInstanceOf(NfoException.class);
        assertThatThrownBy(() -> writer.writeMovie(directory.resolve("fehlt.nfo"), HALLOWEEN))
                .isInstanceOf(NfoException.class);
    }

    private Path givenNfo(String name, String xml) throws IOException {
        return Files.writeString(directory.resolve(name), xml);
    }
}
