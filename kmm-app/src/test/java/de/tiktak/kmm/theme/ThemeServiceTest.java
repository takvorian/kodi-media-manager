package de.tiktak.kmm.theme;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import de.tiktak.kmm.plugin.ScraperRegistry;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.ScraperException;
import de.tiktak.kmm.plugin.api.ThemeScraper;

@ExtendWith(MockitoExtension.class)
class ThemeServiceTest {

    private static final Path DIRECTORY = Path.of("/serien/ALF");
    private static final ThemeTarget ALF = new ThemeTarget(MediaType.TV_SHOW, "ALF", null, 1986, DIRECTORY);
    private static final ThemeTarget HEAT = new ThemeTarget(MediaType.MOVIE, "Heat", null, 1995, Path.of("/filme"));
    private static final URI AUDIO = URI.create("http://televisiontunes.com/uploads/audio/Alf.mp3");

    @Mock
    private ThemeTargets targets;

    @Mock
    private ThemeFinder finder;

    @Mock
    private ThemeFiles files;

    @Mock
    private ScraperRegistry scraperRegistry;

    @Mock
    private ThemeScraper scraper;

    private ThemeService service;

    @BeforeEach
    void setUp() {
        service = new ThemeService(targets, finder, files, scraperRegistry);
        lenient().when(targets.forShow(3L)).thenReturn(ALF);
        lenient().when(targets.forMovie(5L)).thenReturn(HEAT);
        lenient().when(scraper.id()).thenReturn("televisiontunes");
        lenient().when(scraperRegistry.themeScrapersFor(any())).thenReturn(List.of(scraper));
    }

    @Test
    void fillsAMissingThemeWithTheSafeMatchOnly() {
        when(files.find(ALF)).thenReturn(Optional.empty());
        ThemeOption safe = option(true);
        when(finder.find(ALF)).thenReturn(new ThemeOptions(List.of(safe), List.of()));

        assertThat(service.fillShow(3L)).isTrue();

        verify(files).save(ALF, scraper, safe.toCandidate());

        when(finder.find(HEAT)).thenReturn(new ThemeOptions(List.of(option(false)), List.of()));
        when(files.find(HEAT)).thenReturn(Optional.empty());
        assertThat(service.fillMovie(5L)).isFalse();
        verify(files, never()).save(eq(HEAT), any(), any());
    }

    @Test
    void keepsAnExistingTheme() {
        when(files.find(ALF)).thenReturn(Optional.of(DIRECTORY.resolve("theme.mp3")));

        assertThat(service.fillShow(3L)).isFalse();

        verifyNoInteractions(finder);
    }

    @Test
    void appliesAChosenThemeAndReportsProblems() {
        ThemeChoice choice = new ThemeChoice("televisiontunes", AUDIO, AUDIO, "Main Theme", "ALF", AUDIO, null);
        ThemeInfo saved = new ThemeInfo(true, "/serien/ALF/theme.mp3", 3L, null);
        when(files.info(ALF)).thenReturn(saved);

        assertThat(service.applyToShow(3L, choice)).isEqualTo(saved);

        doThrow(new ScraperException("keine Audiodatei")).when(files).save(any(), any(), any());
        assertThatThrownBy(() -> service.applyToShow(3L, choice))
                .isInstanceOfSatisfying(ThemeProblemException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(e.getBody().getDetail()).isEqualTo("keine Audiodatei");
                });
        assertThatThrownBy(() -> service.applyToMovie(5L, new ThemeChoice("youtube", AUDIO, AUDIO, null, null, null,
                null))).isInstanceOfSatisfying(ThemeProblemException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void describesListsFindsAndRemovesThemes() {
        ThemeInfo none = new ThemeInfo(false, "/filme/theme.mp3", null, null);
        when(files.info(HEAT)).thenReturn(none);
        when(files.find(HEAT)).thenReturn(Optional.empty());
        when(files.find(ALF)).thenReturn(Optional.of(DIRECTORY.resolve("theme.mp3")));
        ThemeOptions options = new ThemeOptions(List.of(), List.of());
        when(finder.find(any())).thenReturn(options);

        assertThat(service.movieTheme(5L)).isEqualTo(none);
        assertThat(service.movieCandidates(5L)).isEqualTo(options);
        assertThat(service.showCandidates(3L)).isEqualTo(options);
        assertThat(service.showThemeFile(3L)).isEqualTo(DIRECTORY.resolve("theme.mp3"));
        assertThatThrownBy(() -> service.movieThemeFile(5L))
                .isInstanceOfSatisfying(ThemeProblemException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(service.removeFromMovie(5L)).isEqualTo(none);
        service.removeFromShow(3L);
        service.showTheme(3L);

        verify(files).delete(HEAT);
        verify(files).delete(ALF);
    }

    private static ThemeOption option(boolean sameTitle) {
        return new ThemeOption("Main Theme", sameTitle ? "ALF" : "Alfie", AUDIO, AUDIO, AUDIO, null,
                "televisiontunes", "TelevisionTunes", sameTitle);
    }
}
