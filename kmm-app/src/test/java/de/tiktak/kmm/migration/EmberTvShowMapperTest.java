package de.tiktak.kmm.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import de.tiktak.kmm.migration.ember.library.EmberEpisode;
import de.tiktak.kmm.migration.ember.library.EmberMovie.EmberActor;
import de.tiktak.kmm.migration.ember.library.EmberMovie.EmberRating;
import de.tiktak.kmm.migration.ember.library.EmberStreams;
import de.tiktak.kmm.migration.ember.library.EmberTvShow;
import de.tiktak.kmm.tvshow.EpisodeImport;
import de.tiktak.kmm.tvshow.TvActor;
import de.tiktak.kmm.tvshow.TvRating;
import de.tiktak.kmm.tvshow.TvShowImport;

class EmberTvShowMapperTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final String DIRECTORY = Path.of("/serien/Lost").toAbsolutePath().toString();

    @Test
    void mapsAShowWithAllDetails() {
        EmberTvShow show = EmberTvShow.builder()
                .id(1).sourceId(1).path("\\\\nas\\serien\\Lost").title("Lost").originalTitle("Lost")
                .premiered(LocalDate.of(2004, 9, 22)).plot("Absturz").runtime(45).mpaa("Germany:16").status("Ended")
                .uniqueIds(Map.of("tvdb", "73739", "tmdb", "4607", "imdb", "tt0411008", "unknown", "x"))
                .genres(List.of("Drama")).studios(List.of("ABC")).tags(List.of("Insel"))
                .actors(List.of(new EmberActor("Matthew Fox", "Jack", null)))
                .ratings(List.of(new EmberRating("imdb", 8.3, 10, 500, true), new EmberRating("trakt", 0, 10, 0, false)))
                .seasonTitles(new TreeMap<>(Map.of(0, "Specials")))
                .userRating(11)
                .build();

        TvShowImport imported = EmberTvShowMapper.toImport(show, 5, DIRECTORY);

        assertThat(imported.sourceId()).isEqualTo(5);
        assertThat(imported.directoryPath()).isEqualTo(DIRECTORY);
        assertThat(imported.metadata().title()).isEqualTo("Lost");
        assertThat(imported.metadata().year()).as("follows from the first air date").isNull();
        assertThat(imported.metadata().premiered()).isEqualTo(LocalDate.of(2004, 9, 22));
        assertThat(imported.metadata().mpaa()).isEqualTo("FSK 16");
        assertThat(imported.metadata().status()).isEqualTo("Ended");
        assertThat(imported.metadata().tvdbId()).isEqualTo("73739");
        assertThat(imported.metadata().tmdbId()).isEqualTo("4607");
        assertThat(imported.metadata().imdbId()).isEqualTo("tt0411008");
        assertThat(imported.metadata().userRating()).as("outside the scale").isNull();
        assertThat(imported.metadata().genres()).containsExactly("Drama");
        assertThat(imported.extras().actors()).containsExactly(new TvActor("Matthew Fox", "Jack", null));
        assertThat(imported.extras().ratings()).as("ratings without a value are dropped")
                .containsExactly(new TvRating("imdb", 8.3, 10, 500, true));
        assertThat(imported.extras().seasonNames()).containsExactlyEntriesOf(Map.of(0, "Specials"));
    }

    @Test
    void usesTheDirectoryNameWithoutTitle() {
        EmberTvShow show = EmberTvShow.builder().id(1).path("\\\\nas\\serien\\Lost").build();

        assertThat(EmberTvShowMapper.toImport(show, 5, DIRECTORY).metadata().title()).isEqualTo("Lost");
        assertThat(EmberTvShowMapper.displayName(show)).isEqualTo("\\\\nas\\serien\\Lost");
    }

    @Test
    void mapsAnEpisodeWithAllDetails() {
        LocalDateTime added = LocalDateTime.of(2016, 11, 30, 0, 20, 15);
        EmberEpisode episode = EmberEpisode.builder()
                .id(1).showId(1).path("\\\\nas\\serien\\Lost\\Lost S01E01.mkv").season(1).episode(1)
                .title("Pilot").plot("Absturz").aired(LocalDate.of(2004, 9, 22)).runtime(42)
                .uniqueIds(Map.of("tvdb", "127131", "imdb", "kaputt"))
                .actors(List.of(new EmberActor("Kevin Tighe", "Anthony Cooper", null)))
                .directors(List.of("Jack Bender")).writers(List.of("J. J. Abrams"))
                .ratings(List.of(new EmberRating("tvdb", 8.1, 10, 120, true)))
                .dateAdded(added).playCount(2).lastPlayed(added).userRating(9)
                .streams(new EmberStreams(List.of(), List.of(new EmberStreams.Audio("AAC", "deu", 2, 128)), List.of()))
                .build();

        EpisodeImport imported = EmberTvShowMapper.toImport(episode, "/serien/Lost/Lost S01E01.mkv", BERLIN);

        Instant expected = added.atZone(BERLIN).toInstant();
        assertThat(imported.filePath()).isEqualTo("/serien/Lost/Lost S01E01.mkv");
        assertThat(imported.season()).isEqualTo(1);
        assertThat(imported.episode()).isEqualTo(1);
        assertThat(imported.addedAt()).isEqualTo(expected);
        assertThat(imported.metadata().title()).isEqualTo("Pilot");
        assertThat(imported.metadata().runtime()).isEqualTo(42);
        assertThat(imported.metadata().tvdbId()).isEqualTo("127131");
        assertThat(imported.metadata().imdbId()).as("an invalid ID is dropped").isNull();
        assertThat(imported.metadata().userRating()).isEqualTo(9);
        assertThat(imported.extras().directors()).containsExactly("Jack Bender");
        assertThat(imported.extras().writers()).containsExactly("J. J. Abrams");
        assertThat(imported.extras().actors()).containsExactly(new TvActor("Kevin Tighe", "Anthony Cooper", null));
        assertThat(imported.extras().playCount()).isEqualTo(2);
        assertThat(imported.extras().lastPlayed()).isEqualTo(expected);
        assertThat(imported.extras().streams().audios()).hasSize(1);
    }

    @Test
    void namesEpisodesWithoutTitleByTheirNumber() {
        EmberEpisode episode = EmberEpisode.builder().id(1).showId(1).path("x").season(1).episode(4).build();

        EpisodeImport imported = EmberTvShowMapper.toImport(episode, "/serien/x.mkv", ZoneOffset.UTC);

        assertThat(imported.metadata().title()).isEqualTo("Episode 4");
        assertThat(imported.addedAt()).isNull();
        assertThat(imported.extras().streams().isEmpty()).isTrue();
    }
}
