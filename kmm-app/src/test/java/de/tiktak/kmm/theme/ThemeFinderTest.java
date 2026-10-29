package de.tiktak.kmm.theme;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tiktak.kmm.plugin.ScraperRegistry;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.ScrapeRequest;
import de.tiktak.kmm.plugin.api.ScraperException;
import de.tiktak.kmm.plugin.api.ThemeCandidate;
import de.tiktak.kmm.plugin.api.ThemeScraper;

@ExtendWith(MockitoExtension.class)
class ThemeFinderTest {

    private static final ThemeTarget ALF = new ThemeTarget(MediaType.TV_SHOW, "ALF – Der Außerirdische", "ALF", 1986,
            Path.of("/serien/ALF"));

    @Mock
    private ScraperRegistry scraperRegistry;

    @Mock
    private ThemeScraper scraper;

    private ThemeFinder finder;

    @BeforeEach
    void setUp() {
        finder = new ThemeFinder(scraperRegistry);
        lenient().when(scraper.id()).thenReturn("televisiontunes");
        lenient().when(scraper.displayName()).thenReturn("TelevisionTunes");
        lenient().when(scraper.missingConfiguration()).thenReturn(Optional.empty());
        lenient().when(scraperRegistry.themeScrapersFor(MediaType.TV_SHOW)).thenReturn(List.of(scraper));
    }

    @Test
    void searchesTitleAndOriginalTitleAndPutsSameTitlesFirst() {
        ThemeCandidate other = theme("Alf Remix", "Alfie");
        ThemeCandidate main = theme("Main Theme", "ALF");
        when(scraper.findThemes(request("ALF – Der Außerirdische"))).thenReturn(List.of(other));
        when(scraper.findThemes(request("ALF"))).thenReturn(List.of(other, main));

        ThemeOptions options = finder.find(ALF);

        assertThat(options.candidates()).extracting(ThemeOption::name).containsExactly("Main Theme", "Alf Remix");
        assertThat(options.candidates().getFirst()).satisfies(option -> {
            assertThat(option.sameTitle()).isTrue();
            assertThat(option.scraperId()).isEqualTo("televisiontunes");
            assertThat(option.scraperName()).isEqualTo("TelevisionTunes");
        });
        assertThat(ThemeFinder.safeMatch(options)).map(ThemeOption::name).contains("Main Theme");
    }

    @Test
    void searchesOnceWhenBothTitlesAreAlike() {
        ThemeTarget alf = new ThemeTarget(MediaType.TV_SHOW, "ALF", "Alf", null, Path.of("/serien/ALF"));
        when(scraper.findThemes(any())).thenReturn(List.of(theme("Opening", "Alfred J. Kwak")));

        ThemeOptions options = finder.find(alf);

        verify(scraper).findThemes(any());
        assertThat(ThemeFinder.safeMatch(options)).isEmpty();
    }

    @Test
    void reportsUnconfiguredAndFailingScrapers() {
        ThemeScraper unconfigured = mock(ThemeScraper.class);
        when(unconfigured.missingConfiguration()).thenReturn(Optional.of("YouTube: yt-dlp fehlt"));
        when(scraperRegistry.themeScrapersFor(MediaType.TV_SHOW)).thenReturn(List.of(unconfigured, scraper));
        when(scraper.findThemes(any())).thenThrow(new ScraperException("nicht erreichbar"));

        ThemeOptions options = finder.find(ALF);

        assertThat(options.candidates()).isEmpty();
        assertThat(options.warnings()).containsExactly("YouTube: yt-dlp fehlt", "TelevisionTunes: nicht erreichbar",
                "TelevisionTunes: nicht erreichbar");
        verify(unconfigured, never()).findThemes(any());
    }

    private static ScrapeRequest request(String title) {
        return new ScrapeRequest(MediaType.TV_SHOW, title, 1986, Map.of());
    }

    private static ThemeCandidate theme(String name, String subject) {
        URI audio = URI.create("http://televisiontunes.com/uploads/audio/" + name.replace(' ', '_') + ".mp3");
        return new ThemeCandidate(name, subject, audio, audio, URI.create("http://televisiontunes.com/x.html"), null);
    }
}
