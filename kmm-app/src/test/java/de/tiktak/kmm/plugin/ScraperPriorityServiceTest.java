package de.tiktak.kmm.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.plugin.ScraperOrder.Entry;
import de.tiktak.kmm.plugin.api.EnrichmentScraper;
import de.tiktak.kmm.plugin.api.ImageScraper;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.Scraper;
import de.tiktak.kmm.plugin.api.TvShowScraper;

@ExtendWith(MockitoExtension.class)
class ScraperPriorityServiceTest {

    private static final long SOURCE_ID = 3L;

    @Mock
    private ScraperRegistry registry;

    @Mock
    private ScraperPriorityStore store;

    private ScraperPriorityService service;

    private final TvShowScraper tmdb = scraper(TvShowScraper.class, "tmdb", "TMDB");
    private final TvShowScraper tvdb = scraper(TvShowScraper.class, "tvdb", "TheTVDB");
    private final TvShowScraper other = scraper(TvShowScraper.class, "anidb", "AniDB");

    @BeforeEach
    void setUp() {
        service = new ScraperPriorityService(registry, store, new ScraperDefaults("tmdb", "tvdb"));
    }

    @Test
    void putsThePreferredScraperFirstAndTheOthersByNameWithoutAChosenOrder() {
        assertThat(service.ordered(ScraperKind.METADATA, MediaType.TV_SHOW, null, List.of(tmdb, other, tvdb)))
                .containsExactly(tvdb, other, tmdb);
    }

    @Test
    void followsTheOrderFromTheSettingsAndLeavesOutScrapersSwitchedOff() {
        when(store.global(MediaType.TV_SHOW, ScraperKind.METADATA)).thenReturn(Optional.of(new ScraperOrder(
                List.of(new Entry("tmdb", true), new Entry("gone", true), new Entry("tvdb", false)))));

        assertThat(service.ordered(ScraperKind.METADATA, MediaType.TV_EPISODE, null, List.of(tvdb, other, tmdb)))
                .as("unknown IDs are skipped, scrapers installed later come last")
                .containsExactly(tmdb, other);
    }

    @Test
    void prefersTheOrderOfTheSourceForItsKinds() {
        when(store.ofSource(SOURCE_ID)).thenReturn(Map.of(ScraperKind.METADATA,
                new ScraperOrder(List.of(new Entry("tmdb", true), new Entry("tvdb", true)))));

        assertThat(service.ordered(ScraperKind.METADATA, MediaType.TV_SHOW, SOURCE_ID, List.of(tvdb, tmdb)))
                .containsExactly(tmdb, tvdb);
        when(store.global(MediaType.TV_SHOW, ScraperKind.ARTWORK)).thenReturn(Optional.of(new ScraperOrder(
                List.of(new Entry("tmdb", false)))));
        assertThat(service.ordered(ScraperKind.ARTWORK, MediaType.TV_SEASON, SOURCE_ID, List.of(tvdb, tmdb)))
                .containsExactly(tvdb);
    }

    @Test
    void ordersRatingsByTheirOwnOrderWithoutAChosenOne() {
        EnrichmentScraper trakt = scraper(EnrichmentScraper.class, "trakt", "Trakt");
        EnrichmentScraper ofdb = scraper(EnrichmentScraper.class, "ofdb", "OFDb");
        when(trakt.order()).thenReturn(100);
        when(ofdb.order()).thenReturn(20);

        assertThat(service.ordered(ScraperKind.ENRICHMENT, MediaType.MOVIE_SET, null, List.of(trakt, ofdb)))
                .containsExactly(ofdb, trakt);
    }

    @Test
    void showsAllScrapersOfASourceAndWhetherItFollowsTheSettings() {
        ImageScraper images = scraper(ImageScraper.class, "fanarttv", "fanart.tv");
        when(images.missingConfiguration()).thenReturn(Optional.of(LocalizedText.literal("Key fehlt")));
        when(registry.tvShowScrapers()).thenReturn(List.of(tmdb, tvdb));
        when(registry.imageScrapersFor(MediaType.TV_SHOW)).thenReturn(List.of(images));
        when(store.ofSource(SOURCE_ID)).thenReturn(Map.of(ScraperKind.METADATA,
                new ScraperOrder(List.of(new Entry("tmdb", true), new Entry("tvdb", false)))));

        List<ScraperPriorityView> views = service.ofSource(SOURCE_ID, MediaType.TV_SHOW);

        assertThat(views).extracting(ScraperPriorityView::kind).containsExactly(ScraperKind.METADATA,
                ScraperKind.ARTWORK, ScraperKind.ENRICHMENT, ScraperKind.THEME, ScraperKind.TRAILER);
        assertThat(views.getFirst().inherited()).isFalse();
        assertThat(views.getFirst().entries()).containsExactly(
                new ScraperPriorityView.Entry("tmdb", "TMDB", true, null),
                new ScraperPriorityView.Entry("tvdb", "TheTVDB", false, null));
        assertThat(views.get(1).inherited()).isTrue();
        assertThat(views.get(1).entries()).containsExactly(
                new ScraperPriorityView.Entry("fanarttv", "fanart.tv", true, LocalizedText.literal("Key fehlt")));
    }

    @Test
    void storesTheOrdersOfTheSettingsAndOfASource() {
        ScraperPriorityChange metadata = new ScraperPriorityChange(ScraperKind.METADATA, MediaType.TV_SHOW, false,
                List.of(new ScraperPriorityChange.Entry("tmdb", true), new ScraperPriorityChange.Entry("tvdb", false)));
        ScraperPriorityChange artwork = new ScraperPriorityChange(ScraperKind.ARTWORK, MediaType.TV_SHOW, true,
                List.of());
        ScraperPriorityChange forMovies = new ScraperPriorityChange(ScraperKind.ARTWORK, MediaType.MOVIE, false,
                List.of());

        service.changeGlobal(List.of(metadata));
        service.changeForSource(SOURCE_ID, MediaType.TV_SHOW, List.of(metadata, artwork, forMovies));

        ScraperOrder expected = new ScraperOrder(List.of(new Entry("tmdb", true), new Entry("tvdb", false)));
        verify(store).saveGlobal(MediaType.TV_SHOW, ScraperKind.METADATA, expected);
        verify(store).saveForSource(SOURCE_ID, ScraperKind.METADATA, Optional.of(expected));
        verify(store).saveForSource(SOURCE_ID, ScraperKind.ARTWORK, Optional.empty());
        verify(store, never()).saveForSource(SOURCE_ID, ScraperKind.ARTWORK,
                Optional.of(new ScraperOrder(List.of())));
    }

    @Test
    void offersMovieSetsOnlyForMovies() {
        assertThat(ScraperPriorityService.kindsOf(MediaType.MOVIE)).contains(ScraperKind.MOVIE_SET);
        assertThat(ScraperPriorityService.kindsOf(MediaType.TV_SHOW)).doesNotContain(ScraperKind.MOVIE_SET);
    }

    private static <T extends Scraper> T scraper(Class<T> type, String id, String name) {
        T scraper = mock(type);
        lenient().when(scraper.id()).thenReturn(id);
        lenient().when(scraper.displayName()).thenReturn(name);
        lenient().when(scraper.missingConfiguration()).thenReturn(Optional.empty());
        return scraper;
    }
}
