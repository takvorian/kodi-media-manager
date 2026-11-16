package de.tiktak.kmm.tvshow;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TvShowScraperTest {

    private static final TvEpisodeOrdering NETFLIX = new TvEpisodeOrdering("tvdb", "alternate", "Netflix Order");

    private final TvShow show = new TvShow(1, "/serien/Im Nebelwald", "Im Nebelwald", null);

    @Test
    void dropsAnEpisodeOrderOfAnotherScraper() {
        show.changeEpisodeOrdering(NETFLIX);

        assertThat(show.changeScraper("tmdb")).isTrue();

        assertThat(show.ownScraper()).contains("tmdb");
        assertThat(show.episodeOrdering()).isEmpty();
    }

    @Test
    void keepsAnEpisodeOrderOfTheSameScraper() {
        show.changeEpisodeOrdering(NETFLIX);

        show.changeScraper("tvdb");

        assertThat(show.episodeOrdering()).contains(NETFLIX);
        assertThat(show.changeScraper("tvdb")).isFalse();
        assertThat(show.changeScraper(null)).isTrue();
        assertThat(show.episodeOrdering()).as("it still names its scraper").contains(NETFLIX);
    }

    @Test
    void followsTheScraperOfAChosenEpisodeOrder() {
        show.changeScraper("tmdb");

        show.changeEpisodeOrdering(NETFLIX);

        assertThat(show.ownScraper()).contains("tvdb");
    }
}
