package de.tiktak.kmm.movie;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.tiktak.kmm.nfo.MovieNfo;

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

        MovieNfo nfo = MovieNfoMapper.toNfo(metadata, Map.of("imdb", "tt9999999", "tvdb", "123"));

        assertThat(nfo.title()).isEqualTo("Heat");
        assertThat(nfo.premiered()).isEqualTo(LocalDate.of(1995, 12, 15));
        assertThat(nfo.uniqueIds()).containsExactlyInAnyOrderEntriesOf(
                Map.of("imdb", "tt0113277", "tmdb", "949", "tvdb", "123"));
    }

    @Test
    void dropsClearedImdbIdEvenIfExistingNfoHasOne() {
        MovieMetadata metadata = new MovieMetadata("Heat", null, null, null, null, null, null, null, null, null,
                List.of(), null, null);

        MovieNfo nfo = MovieNfoMapper.toNfo(metadata, Map.of("imdb", "tt0113277"));

        assertThat(nfo.uniqueIds()).isEmpty();
    }
}
