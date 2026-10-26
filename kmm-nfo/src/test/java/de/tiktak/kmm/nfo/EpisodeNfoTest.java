package de.tiktak.kmm.nfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EpisodeNfoTest {

    private final EpisodeNfoReader reader = new EpisodeNfoReader();
    private final EpisodeNfoWriter writer = new EpisodeNfoWriter();

    @TempDir
    Path directory;

    @Test
    void readsAllEpisodesOfAMultiEpisodeFile() throws IOException {
        List<EpisodeNfo> episodes = reader.read(fixture());

        assertThat(episodes).hasSize(2);
        EpisodeNfo first = episodes.getFirst();
        assertThat(first.title()).isEqualTo("Gestatten – Teil 1");
        assertThat(first.season()).isEqualTo(1);
        assertThat(first.episode()).isEqualTo(16);
        assertThat(first.aired()).isEqualTo(LocalDate.of(1987, 2, 9));
        assertThat(first.runtime()).isEqualTo(30);
        assertThat(first.uniqueIds()).containsExactly(Map.entry("tvdb", "110001"));
        assertThat(first.writers()).containsExactly("Bob Bendetson");
        assertThat(first.directors()).containsExactly("Tom Patchett");
        assertThat(first.actors()).containsExactly(new NfoActor("Bill Daily", "Dr. Larry Dykstra", null, null));
        assertThat(first.playCount()).isEqualTo(1);
        assertThat(first.lastPlayed()).isEqualTo(LocalDateTime.of(2014, 5, 25, 1, 21, 47));
        assertThat(first.describes(1, 16)).isTrue();
        assertThat(first.describes(1, 17)).isFalse();
        assertThat(episodes.get(1).describes(1, 17)).isTrue();
    }

    @Test
    void updatesOneEpisodeAndKeepsTheOtherAndUnknownElements() throws IOException {
        Path nfoFile = fixture();
        EpisodeNfo changed = episode(1, 17, "Gestatten – Teil 2 (neu)");

        writer.write(nfoFile, List.of(changed));

        List<EpisodeNfo> episodes = reader.read(nfoFile);
        assertThat(episodes).hasSize(2);
        assertThat(episodes.getFirst().title()).isEqualTo("Gestatten – Teil 1");
        assertThat(episodes.get(1)).isEqualTo(changed);
        String content = Files.readString(nfoFile);
        assertThat(content).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .contains("<codec>ac3</codec>");
        assertThat(content.split("<episodedetails>", -1)).hasSize(3);
    }

    @Test
    void appendsEpisodesThatAreNotInTheFileYet() throws IOException {
        Path nfoFile = fixture();

        writer.write(nfoFile, List.of(episode(1, 18, "Teil 3")));

        assertThat(reader.read(nfoFile)).extracting(EpisodeNfo::episode).containsExactly(16, 17, 18);
    }

    @Test
    void createsANewNfoAndFillsInAFileWithoutNumbers() throws IOException {
        Path created = directory.resolve("S01E01.nfo");
        writer.write(created, List.of(episode(1, 1, "Pilot")));
        assertThat(reader.read(created)).containsExactly(episode(1, 1, "Pilot"));

        Path withoutNumbers = Files.writeString(directory.resolve("S01E02.nfo"),
                "<episodedetails><title>Alt</title><thumb>http://x/y.jpg</thumb></episodedetails>");
        writer.write(withoutNumbers, List.of(episode(1, 2, "Neu")));
        assertThat(reader.read(withoutNumbers)).containsExactly(episode(1, 2, "Neu"));
        assertThat(Files.readString(withoutNumbers)).contains("<thumb>http://x/y.jpg</thumb>");
    }

    @Test
    void requiresSeasonAndEpisodeForWriting() {
        EpisodeNfo withoutNumbers = new EpisodeNfo("x", null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null);

        assertThatThrownBy(() -> writer.write(directory.resolve("x.nfo"), List.of(withoutNumbers)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsFilesWithoutEpisode() throws IOException {
        Path show = Files.writeString(directory.resolve("tvshow.nfo"), "<tvshow><title>ALF</title></tvshow>");

        assertThatThrownBy(() -> reader.read(show)).isInstanceOf(NfoException.class);
    }

    private static EpisodeNfo episode(int season, int episode, String title) {
        return new EpisodeNfo(title, "Original", season, episode, "Handlung", LocalDate.of(1987, 2, 16), 25,
                Map.of("tvdb", "110002"), List.of(new NfoRating("tvdb", 7.5, 10, 12, true)), List.of("Regie"),
                List.of("Autor"), List.of(new NfoActor("Gast", "Rolle", 0, null)), 7, 2,
                LocalDateTime.of(2020, 1, 2, 3, 4, 5), LocalDateTime.of(2019, 1, 1, 0, 0, 0));
    }

    private Path fixture() throws IOException {
        Path target = directory.resolve("ALF - S01E16E17.nfo");
        try (InputStream input = EpisodeNfoTest.class.getResourceAsStream("/nfo/multi-episode.nfo")) {
            Files.copy(input, target);
        }
        return target;
    }
}
