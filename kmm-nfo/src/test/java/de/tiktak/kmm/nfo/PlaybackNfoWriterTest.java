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

        writer.writeMovie(nfo, 1, PLAYED);

        MovieNfo read = new NfoReader().read(nfo);
        assertThat(read.title()).isEqualTo("About Schmidt");
        assertThat(read.supplement().playCount()).isEqualTo(1);
        assertThat(read.supplement().lastPlayed()).isEqualTo(PLAYED);
        assertThat(Files.readString(nfo)).contains("<trailer>plugin://x</trailer>");

        writer.writeMovie(nfo, null, null);
        assertThat(Files.readString(nfo)).doesNotContain("playcount", "lastplayed");
    }

    @Test
    void setsOnlyTheGivenEpisodeOfAMultiEpisodeNfo() throws IOException {
        Path nfo = givenNfo("episode.nfo", """
                <episodedetails><title>Eins</title><season>1</season><episode>1</episode></episodedetails>
                <episodedetails><title>Zwei</title><season>1</season><episode>2</episode></episodedetails>""");

        writer.writeEpisode(nfo, 1, 2, 3, PLAYED);

        List<EpisodeNfo> episodes = new EpisodeNfoReader().read(nfo);
        assertThat(episodes).extracting(EpisodeNfo::title).containsExactly("Eins", "Zwei");
        assertThat(episodes).extracting(EpisodeNfo::playCount).containsExactly(null, 3);
        assertThat(episodes.get(1).lastPlayed()).isEqualTo(PLAYED);
    }

    @Test
    void rejectsMissingNfosAndUnknownEpisodes() throws IOException {
        Path nfo = givenNfo("episode.nfo",
                "<episodedetails><title>Eins</title><season>1</season><episode>1</episode></episodedetails>");

        assertThatThrownBy(() -> writer.writeEpisode(nfo, 1, 5, 1, null)).isInstanceOf(NfoException.class)
                .hasMessageContaining("enthält den Eintrag nicht");
        assertThatThrownBy(() -> writer.writeMovie(directory.resolve("fehlt.nfo"), 1, null))
                .isInstanceOf(NfoException.class);
        assertThat(directory.resolve("fehlt.nfo")).doesNotExist();
    }

    private Path givenNfo(String name, String xml) throws IOException {
        return Files.writeString(directory.resolve(name), xml);
    }
}
