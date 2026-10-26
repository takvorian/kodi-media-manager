package de.tiktak.kmm.plugin.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

class TvShowResultTest {

    @Test
    void rejectsMissingTitle() {
        assertThatNullPointerException()
                .isThrownBy(() -> TvShowResult.builder().build())
                .withMessageContaining("title");
    }

    @Test
    void replacesMissingCollectionsWithEmptyOnes() {
        var show = TvShowResult.builder().title("Lost").build();
        var episode = EpisodeResult.builder().season(1).episode(1).build();

        assertThat(show.genres()).isEmpty();
        assertThat(show.studios()).isEmpty();
        assertThat(show.actors()).isEmpty();
        assertThat(show.ratings()).isEmpty();
        assertThat(show.uniqueIds()).isEmpty();
        assertThat(show.seasons()).isEmpty();
        assertThat(show.episodes()).isEmpty();
        assertThat(episode.directors()).isEmpty();
        assertThat(episode.writers()).isEmpty();
        assertThat(episode.guestStars()).isEmpty();
        assertThat(episode.ratings()).isEmpty();
        assertThat(episode.uniqueIds()).isEmpty();
    }

    @Test
    void copiesCollectionsDefensively() {
        List<EpisodeResult> episodes = new ArrayList<>(List.of(EpisodeResult.builder().season(1).episode(1)
                .title("Pilot").aired(LocalDate.of(2004, 9, 22)).thumb(URI.create("https://example.org/1.jpg"))
                .build()));
        var show = TvShowResult.builder().title("Lost").episodes(episodes)
                .seasons(List.of(new SeasonResult(1, null, null, null))).build();

        episodes.clear();

        assertThat(show.episodes()).extracting(EpisodeResult::title).containsExactly("Pilot");
        assertThat(show.seasons()).extracting(SeasonResult::number).containsExactly(1);
    }

    @Test
    void requiresNamesSourcesAndAttributionTexts() {
        assertThatNullPointerException().isThrownBy(() -> new CastMember(null, "Jack", null));
        assertThatNullPointerException().isThrownBy(() -> new ProviderRating(null, 8.1, 10, null));
        assertThatNullPointerException().isThrownBy(() -> new Attribution(null, URI.create("https://thetvdb.com")));
        assertThatNullPointerException().isThrownBy(() -> new Attribution("TheTVDB", null));
    }

    @Test
    void returnsEpisodesUnchangedWithoutDetails() {
        TvShowScraper scraper = new TvShowScraper() {
            @Override
            public String id() {
                return "stub";
            }

            @Override
            public String displayName() {
                return "Stub";
            }

            @Override
            public Set<MediaType> supportedMediaTypes() {
                return Set.of(MediaType.TV_SHOW);
            }

            @Override
            public List<SearchResult> search(SearchQuery query) {
                return List.of();
            }

            @Override
            public Optional<TvShowResult> fetch(String providerId) {
                return Optional.empty();
            }

            @Override
            public Optional<TvShowResult> scrape(ScrapeRequest request) {
                return Optional.empty();
            }
        };
        EpisodeResult pilot = EpisodeResult.builder().season(1).episode(1).build();

        assertThat(scraper.episodeDetails(pilot)).isSameAs(pilot);
    }
}
