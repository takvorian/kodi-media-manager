package de.tiktak.kmm.migration.ember.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tiktak.kmm.migration.ember.EmberFixtures;
import de.tiktak.kmm.migration.ember.EmberProfileException;
import de.tiktak.kmm.migration.ember.library.EmberMovie.EmberActor;
import de.tiktak.kmm.migration.ember.library.EmberMovie.EmberRating;

class EmberTvLibraryReaderTest {

    private static final EmberTvShowSource SOURCE = new EmberTvShowSource(1, "Serien", "\\\\nas\\serien", "de-DE");

    private final EmberTvLibraryReader reader = new EmberTvLibraryReader();

    @TempDir
    Path directory;

    private EmberTvLibrary library;

    @BeforeEach
    void readFixture() {
        library = reader.read(EmberFixtures.createDatabase(directory, EmberFixtures.DATABASE_FILE_NAME));
    }

    @Test
    void readsSourcesShowsAndOnlyEpisodesWithFile() {
        assertThat(library.sources()).containsExactly(SOURCE);
        assertThat(library.shows()).hasSize((int) EmberFixtures.TV_SHOW_COUNT);
        assertThat(library.episodes()).extracting(EmberEpisode::episode).containsExactly(1, 2, 3);
        assertThat(library.episodesWithoutFile()).isEqualTo(1);
    }

    @Test
    void readsAllDetailsOfAShow() {
        EmberTvShow lost = library.shows().getFirst();

        assertThat(lost.sourceId()).isEqualTo(1);
        assertThat(lost.path()).isEqualTo("\\\\nas\\serien\\Lost");
        assertThat(lost.title()).isEqualTo("Lost");
        assertThat(lost.originalTitle()).isEqualTo("Lost");
        assertThat(lost.sortTitle()).isNull();
        assertThat(lost.premiered()).isEqualTo(LocalDate.of(2004, 9, 22));
        assertThat(lost.plot()).isEqualTo("Überlebende eines Flugzeugabsturzes.");
        assertThat(lost.runtime()).isEqualTo(45);
        assertThat(lost.mpaa()).as("Certification als Ersatz").isEqualTo("Germany:16");
        assertThat(lost.status()).isEqualTo("Ended");
        assertThat(lost.uniqueIds()).containsExactlyInAnyOrderEntriesOf(
                Map.of("tmdb", "4607", "tvdb", "73739", "imdb", "tt0411008"));
        assertThat(lost.genres()).containsExactly("Drama");
        assertThat(lost.studios()).containsExactly("Warner Bros.");
        assertThat(lost.tags()).containsExactly("Heist");
        assertThat(lost.actors()).containsExactly(new EmberActor("Matthew Fox", "Jack Shephard", null));
        assertThat(lost.ratings()).containsExactly(new EmberRating("imdb", 9.9, 10, 1, true));
        assertThat(lost.seasonTitles()).as("Platzhalter -1 und leere Titel entfallen")
                .containsExactlyEntriesOf(Map.of(0, "Specials"));
        assertThat(lost.userRating()).isEqualTo(8);
    }

    @Test
    void readsAllDetailsOfAnEpisode() {
        EmberEpisode pilot = episode(1);

        assertThat(pilot.showId()).isEqualTo(1);
        assertThat(pilot.path()).isEqualTo("\\\\nas\\serien\\Lost\\Season 1\\Lost S01E01E02.mkv");
        assertThat(pilot.season()).isEqualTo(1);
        assertThat(pilot.title()).isEqualTo("Pilot (1)");
        assertThat(pilot.originalTitle()).isNull();
        assertThat(pilot.plot()).isEqualTo("Der Absturz.");
        assertThat(pilot.aired()).isEqualTo(LocalDate.of(2004, 9, 22));
        assertThat(pilot.runtime()).isEqualTo(42);
        assertThat(pilot.uniqueIds()).containsExactlyInAnyOrderEntriesOf(
                Map.of("tvdb", "127131", "tmdb", "64465", "imdb", "tt0636289"));
        assertThat(pilot.actors()).as("Gaststars nach den Darstellern").containsExactly(
                new EmberActor("Matthew Fox", "Jack Shephard", null),
                new EmberActor("Kevin Tighe", "Anthony Cooper", null));
        assertThat(pilot.directors()).containsExactly("Jack Bender");
        assertThat(pilot.writers()).containsExactly("Jack Bender");
        assertThat(pilot.ratings()).containsExactly(new EmberRating("tvdb", 8.1, 10, 120, true));
        assertThat(pilot.dateAdded()).isEqualTo(LocalDateTime.of(2016, 11, 30, 0, 20, 15));
        assertThat(pilot.playCount()).isEqualTo(1);
        assertThat(pilot.lastPlayed()).isNotNull();
        assertThat(pilot.userRating()).isEqualTo(9);
        assertThat(pilot.streams().videos()).containsExactly(
                new EmberStreams.Video("h264", 1280, 720, "1.778", null, null, null, null, null, null, null, null));
        assertThat(pilot.streams().audios()).containsExactly(new EmberStreams.Audio("AAC", "deu", 2, 128));
        assertThat(pilot.streams().subtitles()).containsExactly(new EmberStreams.Subtitle("eng", false, false, null));
    }

    @Test
    void multiEpisodeFileYieldsEpisodesWithTheSamePath() {
        assertThat(episode(2).path()).isEqualTo(episode(1).path());
        assertThat(episode(2).playCount()).isNull();
        assertThat(episode(2).lastPlayed()).isNull();
        assertThat(episode(2).userRating()).isNull();
        assertThat(episode(2).streams()).isEqualTo(EmberStreams.NONE);
        assertThat(episode(3).streams().videos()).extracting(EmberStreams.Video::durationSeconds).containsExactly(2580);
    }

    @Test
    void readsOnlyTheSources() {
        assertThat(reader.readSources(directory.resolve(EmberFixtures.DATABASE_FILE_NAME))).containsExactly(SOURCE);
    }

    @Test
    void rejectsFileThatIsNoEmberDatabase() throws IOException {
        Path notADatabase = Files.writeString(directory.resolve("kaputt.emm"), "kein SQLite");

        assertThatThrownBy(() -> reader.read(notADatabase))
                .isInstanceOf(EmberProfileException.class)
                .hasMessageContaining("kann nicht gelesen werden");
        assertThatThrownBy(() -> reader.readSources(notADatabase)).isInstanceOf(EmberProfileException.class);
    }

    private EmberEpisode episode(int number) {
        List<EmberEpisode> episodes = library.episodes().stream().filter(episode -> episode.episode() == number)
                .toList();
        assertThat(episodes).hasSize(1);
        return episodes.getFirst();
    }
}
