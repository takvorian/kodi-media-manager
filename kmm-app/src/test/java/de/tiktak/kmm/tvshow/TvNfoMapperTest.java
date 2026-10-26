package de.tiktak.kmm.tvshow;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import de.tiktak.kmm.nfo.EpisodeNfo;
import de.tiktak.kmm.nfo.NfoActor;
import de.tiktak.kmm.nfo.NfoRating;
import de.tiktak.kmm.nfo.StreamDetails;
import de.tiktak.kmm.nfo.TvShowNfo;

class TvNfoMapperTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @Test
    void mapsAShowNfoAndBack() {
        TvShowNfo nfo = new TvShowNfo("ALF", "ALF", null, LocalDate.of(1986, 9, 22), "Handlung", 30, "FSK 6", "Ended",
                List.of("Komödie"), List.of("NBC"), List.of("Kult"),
                Map.of("tvdb", "78020", "imdb", "tt0090390", "tmdb", "4658", "trakt", "1"),
                List.of(new NfoRating("tvdb", 7.3, 10, 38, true)),
                List.of(new NfoActor("Paul Fusco", "ALF", 3, null)), new TreeMap<>(Map.of(1, "Ankunft")), 9);

        TvShowMetadata metadata = TvNfoMapper.toMetadata(nfo, "Ordner", 2000);
        TvShowExtras extras = TvNfoMapper.toExtras(nfo);

        assertThat(metadata).isEqualTo(new TvShowMetadata("ALF", "ALF", null, 1986, LocalDate.of(1986, 9, 22),
                "Handlung", 30, "FSK 6", "Ended", List.of("Komödie"), List.of("NBC"), List.of("Kult"), "78020", "4658",
                "tt0090390", 9));
        assertThat(extras.actors()).containsExactly(new TvActor("Paul Fusco", "ALF", null));
        assertThat(extras.seasonNames()).containsExactly(Map.entry(1, "Ankunft"));
        TvShowNfo back = TvNfoMapper.toNfo(metadata, extras);
        assertThat(back.uniqueIds()).containsExactly(Map.entry("tvdb", "78020"), Map.entry("tmdb", "4658"),
                Map.entry("imdb", "tt0090390"));
        assertThat(back.actors()).containsExactly(new NfoActor("Paul Fusco", "ALF", 0, null));
        assertThat(back.ratings()).containsExactly(new NfoRating("tvdb", 7.3, 10, 38, true));
    }

    @Test
    void usesFolderNameAndYearWithoutNfoValues() {
        TvShowNfo empty = new TvShowNfo(null, null, null, null, null, null, null, null, null, null, null,
                Map.of("tvdb", "kaputt"), null, null, null, 42);

        TvShowMetadata metadata = TvNfoMapper.toMetadata(empty, "Ordner", 2000);

        assertThat(metadata.title()).isEqualTo("Ordner");
        assertThat(metadata.year()).isEqualTo(2000);
        assertThat(metadata.tvdbId()).isNull();
        assertThat(metadata.userRating()).isNull();
    }

    @Test
    void mapsAnEpisodeNfoAndBackInLocalTime() {
        EpisodeNfo nfo = new EpisodeNfo(null, "Original", 1, 16, "Handlung", LocalDate.of(1987, 2, 9), 30,
                Map.of("tvdb", "110001"), List.of(), List.of("Regie"), List.of("Autor"),
                List.of(new NfoActor("Gast", "Rolle", 0, null)), 7, 2, LocalDateTime.of(2014, 5, 25, 1, 21, 47),
                null, new StreamDetails(List.of(), List.of(new StreamDetails.Audio("ac3", null, 1, null)), List.of()));

        EpisodeMetadata metadata = TvNfoMapper.toMetadata(nfo, "Aus dem Dateinamen");
        EpisodeExtras extras = TvNfoMapper.toExtras(nfo, BERLIN);

        assertThat(metadata).isEqualTo(new EpisodeMetadata("Aus dem Dateinamen", "Original", "Handlung",
                LocalDate.of(1987, 2, 9), 30, "110001", null, null, 7));
        assertThat(extras.lastPlayed()).isEqualTo(Instant.parse("2014-05-24T23:21:47Z"));
        EpisodeNfo back = TvNfoMapper.toNfo(1, 16, metadata, extras, Instant.parse("2020-01-01T11:00:00Z"), BERLIN);
        assertThat(back.lastPlayed()).isEqualTo(LocalDateTime.of(2014, 5, 25, 1, 21, 47));
        assertThat(back.dateAdded()).isEqualTo(LocalDateTime.of(2020, 1, 1, 12, 0));
        assertThat(back.describes(1, 16)).isTrue();
        assertThat(back.streams()).isEqualTo(nfo.streams());
        assertThat(extras.streams()).isEqualTo(nfo.streams());
        assertThat(TvNfoMapper.toInstant(null, BERLIN)).isNull();
    }
}
