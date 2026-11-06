package de.tiktak.kmm.nfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlaybackNfoWriterTest {

    private static final LocalDateTime PLAYED = LocalDateTime.of(2025, 4, 3, 23, 14, 16);

    private final PlaybackNfoWriter writer = new PlaybackNfoWriter();

    @TempDir
    Path directory;

    @Test
    void setsOnlyPlaybackInAMovieNfo() throws IOException {
        Path nfo = givenNfo("movie.nfo", """
                <movie><title>About Schmidt</title><trailer>plugin://x</trailer><playcount>0</playcount></movie>""");

        writer.writeMovie(nfo, playback(1, PLAYED));

        MovieNfo read = new NfoReader().read(nfo);
        assertThat(read.title()).isEqualTo("About Schmidt");
        assertThat(read.supplement().playCount()).isEqualTo(1);
        assertThat(read.supplement().lastPlayed()).isEqualTo(PLAYED);
        assertThat(Files.readString(nfo)).contains("<trailer>plugin://x</trailer>");

        writer.writeMovie(nfo, playback(null, null));
        assertThat(Files.readString(nfo)).doesNotContain("playcount", "lastplayed");
    }

    @Test
    void setsOnlyTheGivenEpisodeOfAMultiEpisodeNfo() throws IOException {
        Path nfo = givenNfo("episode.nfo", """
                <episodedetails><title>Eins</title><season>1</season><episode>1</episode></episodedetails>
                <episodedetails><title>Zwei</title><season>1</season><episode>2</episode></episodedetails>""");

        writer.writeEpisode(nfo, 1, 2, playback(3, PLAYED));

        List<EpisodeNfo> episodes = new EpisodeNfoReader().read(nfo);
        assertThat(episodes).extracting(EpisodeNfo::title).containsExactly("Eins", "Zwei");
        assertThat(episodes).extracting(EpisodeNfo::playCount).containsExactly(null, 3);
        assertThat(episodes.get(1).lastPlayed()).isEqualTo(PLAYED);
    }

    @Test
    void keepsTheFormattingOfAnEmberNfoCharacterByCharacter() throws IOException {
        String ember = String.join("\r\n",
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>",
                "<!-- Ember Media Manager -->",
                "<episodedetails>",
                "  <uniqueid type=\"tvdb\" default=\"true\">213420</uniqueid>",
                "  <title>The Foretelling</title>",
                "  <season>1</season>",
                "  <episode>1</episode>",
                "  <playcount>0</playcount>",
                "</episodedetails>",
                "");
        Path nfo = givenNfo("episode.nfo", ember);

        writer.writeEpisode(nfo, 1, 1, playback(1, PLAYED));

        assertThat(Files.readString(nfo)).isEqualTo(ember
                .replace("  <playcount>0</playcount>\r\n",
                        "  <playcount>1</playcount>\r\n  <lastplayed>2025-04-03 23:14:16</lastplayed>\r\n"));

        writer.writeEpisode(nfo, 1, 1, playback(null, null));
        assertThat(Files.readString(nfo)).isEqualTo(ember.replace("  <playcount>0</playcount>\r\n", ""));
    }

    @Test
    void insertsIntoTheRightEpisodeAndKeepsUmlauts() throws IOException {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <episodedetails>
                    <title>Grüße</title>
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

        writer.writeEpisode(nfo, 1, 2, playback(3, null));

        assertThat(Files.readString(nfo)).isEqualTo(xml.replace(
                "    <episode>2</episode>\n", "    <episode>2</episode>\n    <playcount>3</playcount>\n"));
    }

    @Test
    void rewritesNfosThatCannotSafelyBeEditedAsText() throws IOException {
        Path nfo = givenNfo("movie.nfo",
                "<movie><title>Heat</title><plot><![CDATA[Ende </movie> mitten im Text]]></plot></movie>");

        writer.writeMovie(nfo, playback(2, PLAYED));

        MovieNfo read = new NfoReader().read(nfo);
        assertThat(read.plot()).isEqualTo("Ende </movie> mitten im Text");
        assertThat(read.supplement().playCount()).isEqualTo(2);
        assertThat(read.supplement().lastPlayed()).isEqualTo(PLAYED);
    }

    @Test
    void rejectsMissingNfosAndUnknownEpisodes() throws IOException {
        Path nfo = givenNfo("episode.nfo",
                "<episodedetails><title>Eins</title><season>1</season><episode>1</episode></episodedetails>");

        assertThatThrownBy(() -> writer.writeEpisode(nfo, 1, 5, playback(1, null))).isInstanceOf(NfoException.class)
                .hasMessageContaining("does not contain the entry");
        assertThatThrownBy(() -> writer.writeMovie(directory.resolve("fehlt.nfo"), playback(1, null)))
                .isInstanceOf(NfoException.class);
        assertThat(directory.resolve("fehlt.nfo")).doesNotExist();
    }

    @Test
    void setsTheUserRatingButKeepsItWhenNoneIsGiven() throws IOException {
        Path nfo = givenNfo("movie.nfo", """
                <movie>
                  <title>Jurassic World</title>
                  <playcount>2</playcount>
                </movie>
                """);

        writer.writeMovie(nfo, new NfoPlayback(2, PLAYED, 10));

        assertThat(Files.readString(nfo)).isEqualTo("""
                <movie>
                  <title>Jurassic World</title>
                  <playcount>2</playcount>
                  <lastplayed>2025-04-03 23:14:16</lastplayed>
                  <userrating>10</userrating>
                </movie>
                """);
        writer.writeMovie(nfo, new NfoPlayback(3, PLAYED, null));
        assertThat(new NfoReader().read(nfo).supplement().userRating()).isEqualTo(10);
    }

    @Test
    void setsTheUserRatingWhenRewritingTheNfo() throws IOException {
        Path nfo = givenNfo("episode.nfo", "<episodedetails><title>Eins</title><season>1</season><episode>1</episode>"
                + "<plot><![CDATA[</episodedetails>]]></plot><userrating>3</userrating></episodedetails>");

        writer.writeEpisode(nfo, 1, 1, new NfoPlayback(1, PLAYED, 8));

        EpisodeNfo episode = new EpisodeNfoReader().read(nfo).getFirst();
        assertThat(episode.userRating()).isEqualTo(8);
        assertThat(episode.plot()).isEqualTo("</episodedetails>");
        writer.writeEpisode(nfo, 1, 1, new NfoPlayback(1, PLAYED, null));
        assertThat(new EpisodeNfoReader().read(nfo).getFirst().userRating()).isEqualTo(8);
    }

    private static NfoPlayback playback(Integer playCount, LocalDateTime lastPlayed) {
        return new NfoPlayback(playCount, lastPlayed, null);
    }

    private Path givenNfo(String name, String xml) throws IOException {
        return Files.writeString(directory.resolve(name), xml);
    }
}
