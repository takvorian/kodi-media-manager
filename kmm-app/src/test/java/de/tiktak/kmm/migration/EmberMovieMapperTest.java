package de.tiktak.kmm.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tiktak.kmm.migration.ember.library.EmberMovie;
import de.tiktak.kmm.migration.ember.library.EmberMovie.EmberActor;
import de.tiktak.kmm.migration.ember.library.EmberMovie.EmberRating;
import de.tiktak.kmm.migration.ember.library.EmberMovie.EmberSetLink;
import de.tiktak.kmm.migration.ember.library.EmberMovieSet;
import de.tiktak.kmm.movie.MovieImport;
import de.tiktak.kmm.movie.MovieMetadata;
import de.tiktak.kmm.movie.MovieSupplement;
import de.tiktak.kmm.scanner.DiscFormat;

class EmberMovieMapperTest {

    /** Ember speichert lokale Uhrzeiten; im November gilt in Berlin UTC+1, im August UTC+2. */
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final Map<Long, EmberMovieSet> SETS = Map.of(
            1L, new EmberMovieSet(1, "Matrix Collection", "Neo gegen die Maschinen.", "2344"),
            2L, new EmberMovieSet(2, null, null, null));

    @TempDir
    Path library;

    @Test
    void mapsMetadataAndConvertsGermanCertification() {
        EmberMovie heat = heat().build();

        MovieImport movieImport = EmberMovieMapper.toImport(heat, 7, file("Heat.mkv"), SETS, BERLIN);

        assertThat(movieImport.sourceId()).isEqualTo(7);
        assertThat(movieImport.discFormat()).isEqualTo(DiscFormat.FILE);
        assertThat(movieImport.partCount()).isEqualTo(1);
        assertThat(movieImport.metadata()).isEqualTo(new MovieMetadata("Heat", "Heat", null, 1995,
                LocalDate.of(1995, 12, 15), "Handlung", null, "Tagline", 170, "FSK 16", List.of("Krimi"),
                "tt0113277", "949", null, null));
        assertThat(movieImport.addedAt()).isEqualTo(Instant.parse("2016-11-29T23:20:15Z"));
    }

    @Test
    void mapsSupplementAndDropsEmptyRatings() {
        EmberMovie heat = heat().build();

        MovieSupplement supplement = EmberMovieMapper.toImport(heat, 7, file("Heat.mkv"), SETS, BERLIN).supplement();

        assertThat(supplement.actors()).containsExactly(new MovieSupplement.Actor("Al Pacino", "Hanna", "pacino.jpg"));
        assertThat(supplement.directors()).containsExactly("Michael Mann");
        assertThat(supplement.ratings()).containsExactly(new MovieSupplement.Rating("imdb", 8.3, 10, 650000, true),
                new MovieSupplement.Rating("trakt", 0, 10, 12, false));
        assertThat(supplement.userRating()).isEqualTo(9);
        assertThat(supplement.lastPlayed()).isEqualTo(Instant.parse("2016-08-13T21:34:55Z"));
        assertThat(supplement.playCount()).isEqualTo(2);
        assertThat(supplement.set()).isNull();
    }

    @Test
    void assignsFirstKnownSetWithOrderAndCollectionId() {
        EmberMovie matrix = heat().sets(List.of(new EmberSetLink(2, 5), new EmberSetLink(1, 3), new EmberSetLink(9, 0)))
                .build();

        MovieImport movieImport = EmberMovieMapper.toImport(matrix, 7, file("Matrix.mkv"), SETS, BERLIN);

        assertThat(movieImport.supplement().set())
                .isEqualTo(new MovieSupplement.SetReference("Matrix Collection", "Neo gegen die Maschinen.", "2344"));
        assertThat(movieImport.setOrder()).isEqualTo(3);
        assertThat(movieImport.setTmdbCollectionId()).isEqualTo("2344");
        assertThat(EmberMovieMapper.belongsToSeveralSets(matrix)).isTrue();
    }

    @Test
    void usesFileNameWhenEmberHasNoTitleAndDropsInvalidValues() {
        EmberMovie untitled = EmberMovie.builder().id(3).sourceId(1).path("D:\\Filme\\x.mkv").year(1700)
                .uniqueIds(Map.of("imdb", "0113277", "tmdb", "abc", "tmdbcol", "99")).userRating(42).build();

        MovieImport movieImport = EmberMovieMapper.toImport(untitled, 7, file("Unbekannt.avi"), SETS, BERLIN);

        assertThat(movieImport.metadata().title()).isEqualTo("Unbekannt");
        assertThat(movieImport.metadata().year()).isNull();
        assertThat(movieImport.metadata().imdbId()).isNull();
        assertThat(movieImport.metadata().tmdbId()).isNull();
        assertThat(movieImport.supplement().userRating()).isNull();
        assertThat(movieImport.setTmdbCollectionId()).as("ID aus der Filmtabelle").isEqualTo("99");
        assertThat(EmberMovieMapper.displayName(untitled)).isEqualTo("D:\\Filme\\x.mkv");
    }

    @Test
    void recognizesDiscsAndStackedFiles() throws IOException {
        Path dvd = library.resolve("Heat (1995)").resolve("VIDEO_TS").resolve("VIDEO_TS.IFO");
        Files.createDirectories(library.resolve("Ronin"));
        Files.createFile(library.resolve("Ronin").resolve("Ronin CD1.avi"));
        Files.createFile(library.resolve("Ronin").resolve("Ronin CD2.avi"));
        EmberMovie untitled = EmberMovie.builder().id(1).sourceId(1).path("x").build();

        MovieImport disc = EmberMovieMapper.toImport(untitled, 7, dvd.toString(), SETS, BERLIN);
        MovieImport stack = EmberMovieMapper.toImport(untitled, 7, file("Ronin/Ronin CD1.avi"), SETS, BERLIN);

        assertThat(disc.discFormat()).isEqualTo(DiscFormat.DVD);
        assertThat(disc.metadata().title()).isEqualTo("Heat (1995)");
        assertThat(EmberMovieMapper.discFormat(file("BDMV/index.bdmv"))).isEqualTo(DiscFormat.BLURAY);
        assertThat(stack.partCount()).isEqualTo(2);
        assertThat(stack.metadata().title()).isEqualTo("Ronin CD1");
    }

    private String file(String relativePath) {
        return library.resolve(relativePath).toString();
    }

    private static EmberMovie.EmberMovieBuilder heat() {
        return EmberMovie.builder()
                .id(1).sourceId(1).path("D:\\Filme\\Heat.mkv").title("Heat").originalTitle("Heat").year(1995)
                .premiered(LocalDate.of(1995, 12, 15)).plot("Handlung").tagline("Tagline").runtime(170)
                .mpaa("Germany:16").uniqueIds(Map.of("imdb", "tt0113277", "tmdb", "949")).genres(List.of("Krimi"))
                .actors(List.of(new EmberActor("Al Pacino", "Hanna", "pacino.jpg"))).directors(List.of("Michael Mann"))
                .ratings(List.of(new EmberRating("imdb", 8.3, 10, 650000, true),
                        new EmberRating("themoviedb", 0, 10, 0, false), new EmberRating("trakt", 0, 10, 12, false)))
                .dateAdded(LocalDateTime.of(2016, 11, 30, 0, 20, 15)).playCount(2)
                .lastPlayed(LocalDateTime.of(2016, 8, 13, 23, 34, 55)).userRating(9);
    }
}
