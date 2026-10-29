package de.tiktak.kmm.nfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TrailerNfoWriterTest {

    private static final String TRAILER = "plugin://plugin.video.youtube/?action=play_video&videoid=gTm8_jO235o";
    private static final String ESCAPED = TRAILER.replace("&", "&amp;");

    private final TrailerNfoWriter writer = new TrailerNfoWriter();

    @TempDir
    Path directory;

    @Test
    void addsOnlyTheTrailerLineToAnEmberShowNfo() throws IOException {
        String ember = String.join("\r\n",
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>",
                "<tvshow>",
                "  <id type=\"tvdb\">347507</id>",
                "  <uniqueid type=\"tvdb\" default=\"true\">347507</uniqueid>",
                "  <mpaa>Germany:16</mpaa>",
                "  <actor>",
                "    <name>Ricky Gervais</name>",
                "    <tmdbid>17835</tmdbid>",
                "  </actor>",
                "</tvshow>");
        Path nfo = Files.writeString(directory.resolve("tvshow.nfo"), ember);

        writer.writeTvShow(nfo, " " + TRAILER + " ");

        assertThat(Files.readString(nfo)).isEqualTo(ember.replace("</tvshow>",
                "  <trailer>" + ESCAPED + "</trailer>\r\n</tvshow>"));
        assertThat(new TvShowNfoReader().read(nfo).trailer()).isEqualTo(TRAILER);
    }

    @Test
    void replacesAndRemovesTheTrailerOfAMovie() throws IOException {
        Path nfo = Files.writeString(directory.resolve("movie.nfo"), """
                <movie>
                    <title>Heat</title>
                    <trailer>https://example.org/alt.mp4</trailer>
                    <mpaa>Germany:16</mpaa>
                </movie>
                """);

        writer.writeMovie(nfo, TRAILER);
        assertThat(Files.readString(nfo)).isEqualTo("""
                <movie>
                    <title>Heat</title>
                    <trailer>%s</trailer>
                    <mpaa>Germany:16</mpaa>
                </movie>
                """.formatted(ESCAPED));

        writer.writeMovie(nfo, null);
        assertThat(Files.readString(nfo)).isEqualTo("""
                <movie>
                    <title>Heat</title>
                    <mpaa>Germany:16</mpaa>
                </movie>
                """);
    }

    @Test
    void rewritesTheFileWhenTheTextCannotBeEditedSafely() throws IOException {
        Path nfo = Files.writeString(directory.resolve("tvshow.nfo"),
                "<tvshow><title>Lost</title><plot><![CDATA[</tvshow>]]></plot></tvshow>");

        writer.writeTvShow(nfo, TRAILER);

        TvShowNfo read = new TvShowNfoReader().read(nfo);
        assertThat(read.trailer()).isEqualTo(TRAILER);
        assertThat(read.title()).isEqualTo("Lost");
        assertThat(read.plot()).isEqualTo("</tvshow>");
    }

    @Test
    void doesNotCreateOrReplaceFilesWithoutTheEntry() throws IOException {
        Path missing = directory.resolve("tvshow.nfo");
        assertThatThrownBy(() -> writer.writeTvShow(missing, TRAILER)).isInstanceOf(NfoException.class);
        assertThat(missing).doesNotExist();

        Path broken = Files.writeString(directory.resolve("movie.nfo"), "<movie><title>kaputt");
        assertThatThrownBy(() -> writer.writeMovie(broken, TRAILER)).isInstanceOf(NfoException.class);
        assertThat(Files.readString(broken)).isEqualTo("<movie><title>kaputt");
    }
}
