package de.tiktak.kmm.nfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LanguageNfoWriterTest {

    private final LanguageNfoWriter writer = new LanguageNfoWriter();

    @TempDir
    Path directory;

    @Test
    void addsOnlyTheLanguageToAMovieNfo() throws IOException {
        String movie = "<movie>\r\n  <title>Heat</title>\r\n</movie>";
        Path nfo = Files.writeString(directory.resolve("Heat.nfo"), movie);

        writer.writeMovie(nfo, "fr-FR");

        assertThat(Files.readString(nfo)).isEqualTo(
                "<movie>\r\n  <title>Heat</title>\r\n  <language>fr-FR</language>\r\n</movie>");
        assertThat(new NfoReader().read(nfo).language()).isEqualTo("fr-FR");
    }

    @Test
    void replacesTheLanguageEmberWroteIntoAShowNfo() throws IOException {
        Path nfo = Files.writeString(directory.resolve("tvshow.nfo"),
                "<tvshow>\n  <title>Lost</title>\n  <language>en-US</language>\n</tvshow>");

        writer.writeTvShow(nfo, "de-DE");

        assertThat(Files.readString(nfo)).contains("<language>de-DE</language>").doesNotContain("en-US");
        assertThat(new TvShowNfoReader().read(nfo).language()).isEqualTo("de-DE");
    }

    @Test
    void removesTheLanguage() throws IOException {
        Path nfo = Files.writeString(directory.resolve("Heat.nfo"),
                "<movie>\n  <title>Heat</title>\n  <language>en-US</language>\n</movie>");

        writer.writeMovie(nfo, null);

        assertThat(Files.readString(nfo)).contains("<title>Heat</title>").doesNotContain("language");
        assertThat(new NfoReader().read(nfo).language()).isNull();
    }

    @Test
    void keepsAnExistingLanguageWhenOtherFieldsAreWritten() throws IOException {
        Path nfo = Files.writeString(directory.resolve("Heat.nfo"),
                "<movie>\n  <title>Heat</title>\n  <language>en-US</language>\n</movie>");

        new NfoWriter().write(nfo, new MovieNfo("Neu", null, null, null, null, null, null, null, null, null, null, null,
                null, null));

        assertThat(Files.readString(nfo)).contains("<title>Neu</title>", "<language>en-US</language>");
    }

    @Test
    void rejectsAMissingNfo() {
        assertThatThrownBy(() -> writer.writeMovie(directory.resolve("fehlt.nfo"), "de-DE"))
                .isInstanceOf(NfoException.class);
    }
}
