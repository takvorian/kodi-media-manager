package de.tiktak.kmm.plugin.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.tiktak.kmm.i18n.LocalizedText;

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
        LocalizedText unreachable = ScraperTexts.unreachable("TMDB", "io");

        assertThat(new ScraperException(unreachable, cause))
                .hasMessage("TMDB cannot be reached: io")
                .hasCause(cause)
                .extracting(ScraperException::localizedText).isEqualTo(unreachable);
        assertThat(new ScraperException(ScraperTexts.apiKeyRejected("TMDB"))).hasNoCause();
    }

    @Test
    void showsUntranslatedMessagesAsTheyAre() {
        var cause = new IllegalStateException("io");

        assertThat(new ScraperException("Invalid API key", cause))
                .hasMessage("Invalid API key")
                .hasCause(cause)
                .extracting(ScraperException::localizedText).isEqualTo(LocalizedText.literal("Invalid API key"));
        assertThat(new ScraperException("Invalid API key")).hasNoCause();
    }
}
