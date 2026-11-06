package de.tiktak.kmm.trailer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.plugin.ScraperRegistry;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.ScrapeRequest;
import de.tiktak.kmm.plugin.api.ScraperException;
import de.tiktak.kmm.plugin.api.TrailerCandidate;
import de.tiktak.kmm.plugin.api.TrailerScraper;

@ExtendWith(MockitoExtension.class)
class TrailerFinderTest {

    private static final ScrapeRequest LOST = new ScrapeRequest(MediaType.TV_SHOW, "Lost", 2004,
            Map.of("tmdb", "4607"));

    @Mock
    private ScraperRegistry scraperRegistry;

    @Mock
    private TrailerScraper scraper;

    private TrailerFinder finder;

    @BeforeEach
    void setUp() {
        finder = new TrailerFinder(scraperRegistry);
        lenient().when(scraper.displayName()).thenReturn("TMDB");
        lenient().when(scraper.missingConfiguration()).thenReturn(Optional.empty());
        lenient().when(scraperRegistry.trailerScrapersFor(MediaType.TV_SHOW)).thenReturn(List.of(scraper));
    }

    @Test
    void listsCandidatesInThePreferredLanguageFirst() {
        when(scraper.findTrailers(LOST)).thenReturn(List.of(
                candidate("Teaser en", "en", "Teaser", true, 1080, "2020-01-01"),
                candidate("Trailer fr", "fr", "Trailer", true, 2160, "2020-01-01"),
                candidate("Trailer en", "en", "Trailer", true, 720, "2020-01-01"),
                candidate("Trailer de inoffiziell", "de", "Trailer", false, 1080, "2021-01-01"),
                candidate("Trailer de", "de", "Trailer", true, 720, null)));

        TrailerOptions options = finder.find(LOST, "de");

        assertThat(options.candidates()).extracting(TrailerOption::name).containsExactly("Trailer de",
                "Trailer de inoffiziell", "Trailer en", "Teaser en", "Trailer fr");
        assertThat(options.candidates().getFirst().scraperName()).isEqualTo("TMDB");
        assertThat(options.warnings()).isEmpty();
    }

    @Test
    void prefersHigherAndNewerTrailersWhenEverythingElseIsEqual() {
        TrailerOption older = TrailerOption.of(candidate("älter", "de", "Trailer", true, 1080, "2019-01-01"), "TMDB");
        TrailerOption newer = TrailerOption.of(candidate("neuer", "de", "Trailer", true, 1080, "2020-01-01"), "TMDB");
        TrailerOption smaller = TrailerOption.of(candidate("klein", "DE", "Trailer", true, null, null), "TMDB");

        List<TrailerOption> sorted = List.of(smaller, older, newer).stream()
                .sorted(TrailerFinder.preference("de")).toList();

        assertThat(sorted).containsExactly(newer, older, smaller);
    }

    @Test
    void reportsUnconfiguredAndFailingScrapersAsWarnings() {
        TrailerScraper unconfigured = mock(TrailerScraper.class);
        when(unconfigured.missingConfiguration())
                .thenReturn(Optional.of(LocalizedText.literal("YouTube: API-Key fehlt")));
        when(scraperRegistry.trailerScrapersFor(MediaType.TV_SHOW)).thenReturn(List.of(unconfigured, scraper));
        when(scraper.findTrailers(any())).thenThrow(new ScraperException("nicht erreichbar"));

        TrailerOptions options = finder.find(LOST, "de");

        assertThat(options.candidates()).isEmpty();
        assertThat(options.warnings()).containsExactly("YouTube: API-Key fehlt", "TMDB: nicht erreichbar");
        verify(unconfigured, never()).findTrailers(any());
    }

    @Test
    void acceptsOnlyLinksKodiCanPlay() {
        assertThat(TrailerFinder.isPlayable("plugin://plugin.video.youtube/?action=play_video&videoid=x")).isTrue();
        assertThat(TrailerFinder.isPlayable("HTTPS://example.org/trailer.mp4")).isTrue();
        assertThat(TrailerFinder.isPlayable("smb://nas/trailer.mkv")).isTrue();
        assertThat(TrailerFinder.isPlayable("nfs://nas/trailer.mkv")).isTrue();
        for (String invalid : new String[] {"YouTube", "ftp://server/trailer.mp4", "plugin://a b", "", null}) {
            assertThat(TrailerFinder.isPlayable(invalid)).as(String.valueOf(invalid)).isFalse();
        }
    }

    private static TrailerCandidate candidate(String name, String language, String type, boolean official,
            Integer height, String published) {
        String id = name.replace(' ', '-');
        return new TrailerCandidate(name, "plugin://plugin.video.youtube/?action=play_video&videoid=" + id,
                URI.create("https://www.youtube.com/watch?v=" + id), null, language, type, official, height,
                published);
    }
}
