package de.tiktak.kmm.movie;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.tiktak.kmm.nfo.MovieNfo;
import de.tiktak.kmm.nfo.MovieNfoSupplement;
import de.tiktak.kmm.nfo.NfoActor;
import de.tiktak.kmm.nfo.NfoMovieSet;
import de.tiktak.kmm.nfo.NfoRating;

class MovieNfoMapperTest {

    private static final MovieNfo FULL_NFO = new MovieNfo("Heat", "Heat OT", "Heat Sort", 1995,
            LocalDate.of(1995, 12, 15), "Handlung", "Kurz", "Tagline", 170, "FSK 16", List.of("Krimi"),
            Map.of("imdb", "tt0113277", "tmdb", "949", "tvdb", "123"));

    @Test
    void mapsAllNfoFieldsToMetadata() {
        MovieMetadata metadata = MovieNfoMapper.toMetadata(FULL_NFO, "Datei-Titel", 2000);

        assertThat(metadata).isEqualTo(new MovieMetadata("Heat", "Heat OT", "Heat Sort", 1995,
                LocalDate.of(1995, 12, 15), "Handlung", "Kurz", "Tagline", 170, "FSK 16", List.of("Krimi"),
                "tt0113277", "949"));
    }

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @Test
    void mapsSupplementSortingActorsByTheirOrder() {
        var nfo = new MovieNfoSupplement(
                List.of(new NfoActor("De Niro", "McCauley", 1, null), new NfoActor("Kilmer", null, null, null),
                        new NfoActor("Pacino", "Hanna", 0, "pacino.jpg")),
                List.of("Michael Mann"), List.of("Michael Mann"), List.of("Warner"), List.of("USA"), List.of("Heist"),
                List.of(new NfoRating("imdb", 8.3, 10, 650000, true)), new NfoMovieSet("Mann Collection", "Filme"),
                0, 2, LocalDateTime.of(2024, 5, 1, 20, 15), null);

        MovieSupplement supplement = MovieNfoMapper.toSupplement(nfo, BERLIN);

        assertThat(supplement.actors()).extracting(MovieSupplement.Actor::name)
                .containsExactly("Pacino", "De Niro", "Kilmer");
        assertThat(supplement.actors().getFirst()).isEqualTo(new MovieSupplement.Actor("Pacino", "Hanna", "pacino.jpg"));
        assertThat(supplement.ratings()).containsExactly(new MovieSupplement.Rating("imdb", 8.3, 10, 650000, true));
        assertThat(supplement.set()).isEqualTo(new MovieSupplement.SetReference("Mann Collection", "Filme"));
        assertThat(supplement.userRating()).isNull();
        assertThat(supplement.playCount()).isEqualTo(2);
        assertThat(supplement.lastPlayed()).isEqualTo(Instant.parse("2024-05-01T18:15:00Z"));
    }

    @Test
    void mapsSupplementBackToNfoWithOrderAndLocalTimes() {
        var supplement = new MovieSupplement(List.of(new MovieSupplement.Actor("Pacino", "Hanna", null),
                new MovieSupplement.Actor("De Niro", null, null)), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(new MovieSupplement.Rating("imdb", 8.3, 10, null, true)),
                new MovieSupplement.SetReference("Mann Collection", null), 9, null, Instant.parse("2024-05-01T18:15:00Z"));

        MovieNfoSupplement nfo = MovieNfoMapper.toNfoSupplement(supplement, Instant.parse("2016-11-30T00:20:15Z"),
                BERLIN);

        assertThat(nfo.actors()).containsExactly(new NfoActor("Pacino", "Hanna", 0, null),
                new NfoActor("De Niro", null, 1, null));
        assertThat(nfo.ratings()).containsExactly(new NfoRating("imdb", 8.3, 10, null, true));
        assertThat(nfo.set()).isEqualTo(new NfoMovieSet("Mann Collection", null));
        assertThat(nfo.userRating()).isEqualTo(9);
        assertThat(nfo.lastPlayed()).isEqualTo(LocalDateTime.of(2024, 5, 1, 20, 15));
        assertThat(nfo.dateAdded()).isEqualTo(LocalDateTime.of(2016, 11, 30, 1, 20, 15));
    }

    @Test
    void mapsEmptySupplementWithoutSetAndTimes() {
        assertThat(MovieNfoMapper.toSupplement(MovieNfoSupplement.EMPTY, BERLIN)).isEqualTo(MovieSupplement.EMPTY);
        assertThat(MovieNfoMapper.toNfoSupplement(MovieSupplement.EMPTY, null, BERLIN))
                .isEqualTo(MovieNfoSupplement.EMPTY);
    }

    @Test
    void fallsBackToScannedTitleAndYear() {
        MovieNfo empty = new MovieNfo(null, null, null, null, null, null, null, null, null, null, null, null);

        MovieMetadata metadata = MovieNfoMapper.toMetadata(empty, "Heat", 1995);

        assertThat(metadata.title()).isEqualTo("Heat");
        assertThat(metadata.year()).isEqualTo(1995);
        assertThat(metadata.genres()).isEmpty();
        assertThat(metadata.imdbId()).isNull();
    }

    @Test
    void usesYearOfPremieredBeforeScannedYear() {
        MovieNfo nfo = new MovieNfo(null, null, null, null, LocalDate.of(1995, 12, 15), null, null, null, null, null,
                null, null);

        assertThat(MovieNfoMapper.toMetadata(nfo, "Heat", 2000).year()).isEqualTo(1995);
    }

    @Test
    void writesMetadataAndKeepsForeignIds() {
        MovieMetadata metadata = MovieNfoMapper.toMetadata(FULL_NFO, "Heat", null);

        MovieNfo nfo = MovieNfoMapper.toNfo(metadata, null, Map.of("imdb", "tt9999999", "tvdb", "123"));

        assertThat(nfo.title()).isEqualTo("Heat");
        assertThat(nfo.premiered()).isEqualTo(LocalDate.of(1995, 12, 15));
        assertThat(nfo.uniqueIds()).containsExactlyInAnyOrderEntriesOf(
                Map.of("imdb", "tt0113277", "tmdb", "949", "tvdb", "123"));
    }

    @Test
    void dropsClearedImdbIdEvenIfExistingNfoHasOne() {
        MovieMetadata metadata = new MovieMetadata("Heat", null, null, null, null, null, null, null, null, null,
                List.of(), null, null);

        MovieNfo nfo = MovieNfoMapper.toNfo(metadata, null, Map.of("imdb", "tt0113277"));

        assertThat(nfo.uniqueIds()).isEmpty();
    }
}
