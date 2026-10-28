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
import de.tiktak.kmm.migration.ember.library.EmberMovie.EmberSetLink;

class EmberLibraryReaderTest {

    private final EmberLibraryReader reader = new EmberLibraryReader();

    @TempDir
    Path directory;

    private EmberLibrary library;

    @BeforeEach
    void readFixture() {
        library = reader.read(EmberFixtures.createDatabase(directory, EmberFixtures.DATABASE_FILE_NAME));
    }

    @Test
    void readsSourcesSetsAndExcludedDirectories() {
        assertThat(library.sources()).containsExactly(new EmberMovieSource(1, "Filme", "D:\\Filme", "de-DE"));
        assertThat(library.sets()).containsExactly(
                new EmberMovieSet(1, "Matrix Collection", "Neo gegen die Maschinen.", "2344"),
                new EmberMovieSet(2, "Leeres Set", null, "999", true));
        assertThat(library.setsById()).containsOnlyKeys(1L, 2L);
        assertThat(library.excludedDirectories()).containsExactly("D:\\Filme\\Privat");
        assertThat(library.movies()).hasSize((int) EmberFixtures.MOVIE_COUNT);
    }

    @Test
    void readsAllDetailsOfAMovie() {
        EmberMovie heat = movie(1);

        assertThat(heat.sourceId()).isEqualTo(1);
        assertThat(heat.path()).isEqualTo("D:\\Filme\\Heat (1995)\\Heat.mkv");
        assertThat(heat.title()).isEqualTo("Heat");
        assertThat(heat.sortTitle()).isNull();
        assertThat(heat.year()).isEqualTo(1995);
        assertThat(heat.premiered()).isEqualTo(LocalDate.of(1995, 12, 15));
        assertThat(heat.outline()).isNull();
        assertThat(heat.tagline()).isEqualTo("A Los Angeles Crime Saga");
        assertThat(heat.runtime()).isEqualTo(170);
        assertThat(heat.mpaa()).isEqualTo("Germany:16");
        assertThat(heat.uniqueIds()).containsExactlyInAnyOrderEntriesOf(Map.of("imdb", "tt0113277", "tmdb", "949"));
        assertThat(heat.genres()).containsExactly("Krimi", "Drama");
        assertThat(heat.actors()).containsExactly(
                new EmberActor("Al Pacino", "Lt. Vincent Hanna", "https://image.tmdb.org/t/p/original/pacino.jpg"),
                new EmberActor("Robert De Niro", "Neil McCauley", null));
        assertThat(heat.directors()).containsExactly("Michael Mann");
        assertThat(heat.writers()).containsExactly("Michael Mann");
        assertThat(heat.studios()).containsExactly("Regency", "Warner Bros.");
        assertThat(heat.countries()).containsExactly("USA");
        assertThat(heat.tags()).containsExactly("Heist");
        assertThat(heat.ratings()).containsExactly(new EmberRating("imdb", 8.3, 10, 650000, true),
                new EmberRating("themoviedb", 7.9, 10, 1000, false), new EmberRating("trakt", 0, 10, 0, false));
        assertThat(heat.sets()).isEmpty();
        assertThat(heat.dateAdded()).isEqualTo(LocalDateTime.of(2016, 11, 30, 0, 20, 15));
        assertThat(heat.playCount()).isEqualTo(2);
        assertThat(heat.lastPlayed()).isEqualTo(LocalDateTime.of(2016, 8, 13, 23, 34, 55));
        assertThat(heat.userRating()).isEqualTo(9);
        assertThat(heat.edition()).isEqualTo("Director's Cut");
        assertThat(heat.videoSource()).isEqualTo("bluray");
    }

    @Test
    void takesIdsFromMovieColumnsAndNormalizesTheirType() {
        EmberMovie matrix = movie(2);

        assertThat(matrix.uniqueIds()).containsExactlyInAnyOrderEntriesOf(
                Map.of("tmdb", "603", "imdb", "tt0133093", "tmdbcol", "2344"));
        assertThat(matrix.runtime()).isEqualTo(136);
        assertThat(matrix.sets()).containsExactly(new EmberSetLink(1, 0));
        assertThat(matrix.playCount()).isNull();
        assertThat(matrix.lastPlayed()).isNull();
        assertThat(matrix.userRating()).isNull();
        assertThat(matrix.edition()).as("leer").isNull();
        assertThat(matrix.videoSource()).as("leer").isNull();
    }

    @Test
    void treatsEmptyAndInvalidValuesAsMissing() {
        EmberMovie reloaded = movie(3);

        assertThat(reloaded.year()).isNull();
        assertThat(reloaded.premiered()).isNull();
        assertThat(reloaded.mpaa()).as("Certification als Ersatz").isEqualTo("Germany:12");
        assertThat(reloaded.actors()).containsExactly(new EmberActor("Keanu Reeves", null, null));
        assertThat(reloaded.sets()).containsExactly(new EmberSetLink(1, null));
        assertThat(reloaded.dateAdded()).isNull();
        assertThat(reloaded.studios()).isEmpty();
        assertThat(reloaded.ratings()).isEmpty();
        assertThat(reloaded.streams()).isEqualTo(EmberStreams.NONE);
    }

    @Test
    void readsStreamDetailsOfAMovie() {
        EmberStreams streams = movie(1).streams();

        assertThat(streams.videos()).containsExactly(
                new EmberStreams.Video("avc1", 1920, 800, "2.40", 10200, 8000, "progressive", null, "de", "2",
                        "left_right", 2167309437L));
        assertThat(streams.audios()).containsExactly(
                new EmberStreams.Audio("DTS", "deu", 6, 1509),
                new EmberStreams.Audio("AC3", "eng", 6, null));
        assertThat(streams.subtitles()).containsExactly(new EmberStreams.Subtitle("deu", true, true,
                "D:\\Filme\\Heat (1995)\\Heat.de.srt"));
    }

    @Test
    void readsOnlyTheSources() {
        assertThat(reader.readSources(directory.resolve(EmberFixtures.DATABASE_FILE_NAME)))
                .containsExactly(new EmberMovieSource(1, "Filme", "D:\\Filme", "de-DE"));
    }

    @Test
    void rejectsFileThatIsNoEmberDatabase() throws IOException {
        Path notADatabase = Files.writeString(directory.resolve("kaputt.emm"), "kein SQLite");

        assertThatThrownBy(() -> reader.read(notADatabase))
                .isInstanceOf(EmberProfileException.class)
                .hasMessageContaining("kann nicht gelesen werden");
        assertThatThrownBy(() -> reader.readSources(notADatabase)).isInstanceOf(EmberProfileException.class);
    }

    private EmberMovie movie(long id) {
        List<EmberMovie> movies = library.movies().stream().filter(movie -> movie.id() == id).toList();
        assertThat(movies).hasSize(1);
        return movies.getFirst();
    }
}
