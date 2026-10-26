package de.tiktak.kmm.plugin.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ScraperTest {

    private final ImageScraper movieOnlyScraper = new ImageScraper() {

        @Override
        public String id() {
            return "movie-only";
        }

        @Override
        public String displayName() {
            return "Movie only";
        }

        @Override
        public Set<MediaType> supportedMediaTypes() {
            return Set.of(MediaType.MOVIE);
        }

        @Override
        public List<ImageCandidate> findImages(ScrapeRequest request) {
            return List.of();
        }
    };

    @Test
    void supportsDeclaredMediaType() {
        assertThat(movieOnlyScraper.supports(MediaType.MOVIE)).isTrue();
    }

    @Test
    void doesNotSupportUndeclaredMediaType() {
        assertThat(movieOnlyScraper.supports(MediaType.TV_SHOW)).isFalse();
    }

    @Test
    void isReadyAndIgnoresContextByDefault() {
        movieOnlyScraper.initialize(null);

        assertThat(movieOnlyScraper.missingConfiguration()).isEmpty();
        assertThat(movieOnlyScraper.attribution()).isEmpty();
    }

    @Test
    void carriesUserFacingMessage() {
        var cause = new IllegalStateException("io");

        assertThat(new ScraperException("TMDB nicht erreichbar", cause))
                .hasMessage("TMDB nicht erreichbar")
                .hasCause(cause);
        assertThat(new ScraperException("Ungültiger API-Key")).hasNoCause();
    }
}
