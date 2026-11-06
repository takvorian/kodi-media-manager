package de.tiktak.kmm.artwork;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.plugin.ScraperRegistry;
import de.tiktak.kmm.plugin.api.ImageCandidate;
import de.tiktak.kmm.plugin.api.ImageScraper;
import de.tiktak.kmm.plugin.api.ImageType;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.ScrapeRequest;
import de.tiktak.kmm.plugin.api.ScraperException;
import de.tiktak.kmm.settings.KmmSettings;
import de.tiktak.kmm.settings.SettingsService;

class ArtworkCandidateFinderTest {

    private static final Map<String, String> IDS = Map.of("tmdb", "949");

    private final ScraperRegistry registry = mock(ScraperRegistry.class);
    private final ArtworkCandidateFinder finder = new ArtworkCandidateFinder(registry, settings());

    @Test
    void mergesScrapersAndSortsByLanguageThenRating() {
        ImageScraper tmdb = scraper("tmdb", "TMDB", List.of(
                image(ImageType.POSTER, "en", 9.0, "tmdb-en"),
                image(ImageType.POSTER, "de", 5.0, "tmdb-de"),
                image(ImageType.FANART, null, 8.0, "tmdb-fanart")));
        ImageScraper fanart = scraper("fanarttv", "fanart.tv", List.of(
                image(ImageType.POSTER, "de", 7.0, "fanart-de"),
                image(ImageType.POSTER, null, 1.0, "fanart-neutral"),
                image(ImageType.POSTER, "fr", 10.0, "fanart-fr"),
                image(ImageType.POSTER, "de", null, "fanart-de-unrated")));
        when(registry.imageScrapersFor(MediaType.MOVIE)).thenReturn(List.of(tmdb, fanart));

        ArtworkCandidates result = finder.find(ImageType.POSTER, "Heat", 1995, IDS);

        assertThat(result.candidates()).extracting(candidate -> candidate.url().getPath())
                .containsExactly("/fanart-de", "/tmdb-de", "/fanart-de-unrated", "/fanart-neutral", "/tmdb-en",
                        "/fanart-fr");
        assertThat(result.candidates().getFirst()).satisfies(candidate -> {
            assertThat(candidate.scraperId()).isEqualTo("fanarttv");
            assertThat(candidate.scraperName()).isEqualTo("fanart.tv");
        });
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void prefersTextlessFanart() {
        ImageScraper tmdb = scraper("tmdb", "TMDB", List.of(
                image(ImageType.FANART, "de", 0.0, "fanart-de"),
                image(ImageType.FANART, null, 6.0, "fanart-neutral"),
                image(ImageType.FANART, "en", 9.0, "fanart-en")));
        when(registry.imageScrapersFor(MediaType.MOVIE)).thenReturn(List.of(tmdb));

        assertThat(finder.find(ImageType.FANART, "Heat", 1995, IDS).candidates())
                .extracting(candidate -> candidate.url().getPath())
                .containsExactly("/fanart-neutral", "/fanart-de", "/fanart-en");
    }

    @Test
    void passesIdsTitleAndYearToTheScrapers() {
        ImageScraper tmdb = scraper("tmdb", "TMDB", List.of());
        when(registry.imageScrapersFor(MediaType.MOVIE)).thenReturn(List.of(tmdb));

        finder.find(ImageType.POSTER, "Heat", 1995, IDS);

        verify(tmdb).findImages(new ScrapeRequest(MediaType.MOVIE, "Heat", 1995, IDS));
    }

    @Test
    void reportsUnconfiguredAndFailingScrapersWithoutLosingOtherResults() {
        ImageScraper working = scraper("tmdb", "TMDB", List.of(image(ImageType.POSTER, "de", 5.0, "ok")));
        ImageScraper unconfigured = scraper("fanarttv", "fanart.tv", List.of());
        when(unconfigured.missingConfiguration()).thenReturn(Optional.of("Für fanart.tv fehlt ein API-Key."));
        ImageScraper failing = scraper("broken", "Kaputt", List.of());
        when(failing.findImages(any())).thenThrow(new ScraperException("nicht erreichbar"));
        ImageScraper crashing = scraper("crash", "Absturz", List.of());
        when(crashing.findImages(any())).thenThrow(new IllegalStateException("Bug"));
        when(registry.imageScrapersFor(MediaType.MOVIE)).thenReturn(List.of(working, unconfigured, failing, crashing));

        ArtworkCandidates result = finder.find(ImageType.POSTER, "Heat", 1995, IDS);

        assertThat(result.candidates()).hasSize(1);
        assertThat(result.warnings()).containsExactly(
                LocalizedText.literal("Für fanart.tv fehlt ein API-Key."),
                LocalizedText.of("artwork.warning.provider").with("provider", "Kaputt")
                        .with("reason", LocalizedText.literal("nicht erreichbar")),
                LocalizedText.of("artwork.warning.provider").with("provider", "Absturz")
                        .with("reason", LocalizedText.of("artwork.warning.unexpectedError")));
        verify(unconfigured, never()).findImages(any());
    }

    @Test
    void groupsAllTypesWithOneRequestPerScraper() {
        ImageScraper tmdb = scraper("tmdb", "TMDB", List.of(
                image(ImageType.POSTER, "de", 1.0, "poster"),
                image(ImageType.FANART, null, 2.0, "fanart"),
                image(ImageType.CLEARLOGO, "en", 3.0, "logo")));
        when(registry.imageScrapersFor(MediaType.MOVIE)).thenReturn(List.of(tmdb));

        ArtworkSearch search = finder.findAll(MediaType.MOVIE, "Heat", 1995, IDS);

        assertThat(search.candidatesOf(ImageType.POSTER)).hasSize(1);
        assertThat(search.candidatesOf(ImageType.CLEARLOGO)).hasSize(1);
        assertThat(search.candidatesOf(ImageType.BANNER)).isEmpty();
        verify(tmdb, times(1)).findImages(any());
    }

    @Test
    void returnsNothingWithoutScrapers() {
        when(registry.imageScrapersFor(MediaType.MOVIE)).thenReturn(List.of());

        assertThat(finder.find(ImageType.POSTER, "Heat", null, IDS).candidates()).isEmpty();
    }

    private static ImageScraper scraper(String id, String name, List<ImageCandidate> images) {
        ImageScraper scraper = mock(ImageScraper.class);
        when(scraper.id()).thenReturn(id);
        when(scraper.displayName()).thenReturn(name);
        when(scraper.missingConfiguration()).thenReturn(Optional.empty());
        when(scraper.findImages(any())).thenReturn(images);
        return scraper;
    }

    private static ImageCandidate image(ImageType type, String language, Double rating, String path) {
        return ImageCandidate.builder().type(type).url(URI.create("https://img.example.org/" + path))
                .language(language).rating(rating).build();
    }

    private static SettingsService settings() {
        SettingsService settings = mock(SettingsService.class);
        when(settings.current()).thenReturn(KmmSettings.DEFAULTS);
        return settings;
    }
}
