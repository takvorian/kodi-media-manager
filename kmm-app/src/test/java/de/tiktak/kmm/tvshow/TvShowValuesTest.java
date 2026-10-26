package de.tiktak.kmm.tvshow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

class TvShowValuesTest {

    @Test
    void trimsAndLimitsShowMetadata() {
        String longTitle = "T".repeat(TvTexts.MAX_TITLE_LENGTH + 10);
        TvShowMetadata metadata = new TvShowMetadata(" " + longTitle + " ", " ", null, 1500, null, "  ", -5, " FSK 12 ",
                null, Arrays.asList(" Drama ", "Drama", " ", null), List.of("NBC"), null, " 123 ", "abc", "tt123",
                11);

        assertThat(metadata.originalTitle()).isNull();
        assertThat(metadata.genres()).containsExactly("Drama");
        assertThat(metadata.mpaa()).isEqualTo("FSK 12");
        assertThat(metadata.tags()).isNull();

        TvShowMetadata limited = metadata.withinLimits();

        assertThat(limited.title()).hasSize(TvTexts.MAX_TITLE_LENGTH);
        assertThat(limited.year()).isNull();
        assertThat(limited.runtime()).isNull();
        assertThat(limited.tags()).isEmpty();
        assertThat(limited.tvdbId()).isEqualTo("123");
        assertThat(limited.tmdbId()).isNull();
        assertThat(limited.imdbId()).isNull();
        assertThat(limited.userRating()).isNull();
    }

    @Test
    void limitsEpisodeMetadata() {
        EpisodeMetadata limited = new EpisodeMetadata(" Pilot ", null, "P".repeat(TvTexts.MAX_PLOT_LENGTH + 1), null, -1,
                "x", "42", "tt0000001", 0).withinLimits();

        assertThat(limited.title()).isEqualTo("Pilot");
        assertThat(limited.plot()).hasSize(TvTexts.MAX_PLOT_LENGTH);
        assertThat(limited.runtime()).isNull();
        assertThat(limited.tvdbId()).isNull();
        assertThat(limited.tmdbId()).isEqualTo("42");
        assertThat(limited.imdbId()).isEqualTo("tt0000001");
        assertThat(limited.userRating()).isNull();
    }

    @Test
    void dropsIncompleteExtras() {
        TvShowExtras show = new TvShowExtras(List.of(new TvActor(" ", "Rolle", null), new TvActor("ALF", " ", " ")),
                List.of(new TvRating(" ", 1, null, null, false)), new TreeMap<>(Map.of(-1, "x", 1, " ", 2, "Zwei")));
        EpisodeExtras episode = new EpisodeExtras(Arrays.asList("Regie", "Regie", null), null, null, null, -3, null,
                null);

        assertThat(show.actors()).containsExactly(new TvActor("ALF", null, null));
        assertThat(show.ratings()).isEmpty();
        assertThat(show.seasonNames()).containsExactly(Map.entry(2, "Zwei"));
        assertThat(new TvShowExtras(null, null, null)).isEqualTo(TvShowExtras.EMPTY);
        assertThat(episode.directors()).containsExactly("Regie");
        assertThat(episode.writers()).isEmpty();
        assertThat(episode.playCount()).isNull();
        assertThat(new EpisodeExtras(null, null, null, null, null, null, null)).isEqualTo(EpisodeExtras.EMPTY);
    }

    @Test
    void hashesPaths() {
        assertThat(PathHash.of("/media/serien/ALF")).hasSize(64).isEqualTo(PathHash.of("/media/serien/ALF"))
                .isNotEqualTo(PathHash.of("/media/serien/alf"));
    }
}
