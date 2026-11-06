package de.tiktak.kmm.theme;

import java.nio.file.Path;
import java.util.Optional;

import org.springframework.stereotype.Service;

import de.tiktak.kmm.plugin.ScraperRegistry;
import de.tiktak.kmm.plugin.api.ScraperException;
import de.tiktak.kmm.plugin.api.ThemeScraper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Theme music of movies and TV shows as {@code theme.mp3} in the folder: show suggestions, apply or
 * remove one and add missing ones automatically, but only for a safe match (same title at the provider).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThemeService {

    private final ThemeTargets targets;
    private final ThemeFinder finder;
    private final ThemeFiles files;
    private final ScraperRegistry scraperRegistry;

    public ThemeInfo movieTheme(long movieId) {
        return files.info(targets.forMovie(movieId));
    }

    public ThemeInfo showTheme(long showId) {
        return files.info(targets.forShow(showId));
    }

    public ThemeOptions movieCandidates(long movieId) {
        return finder.find(targets.forMovie(movieId));
    }

    public ThemeOptions showCandidates(long showId) {
        return finder.find(targets.forShow(showId));
    }

    public ThemeInfo applyToMovie(long movieId, ThemeChoice choice) {
        return apply(targets.forMovie(movieId), choice.toOption());
    }

    public ThemeInfo applyToShow(long showId, ThemeChoice choice) {
        return apply(targets.forShow(showId), choice.toOption());
    }

    public ThemeInfo removeFromMovie(long movieId) {
        return remove(targets.forMovie(movieId));
    }

    public ThemeInfo removeFromShow(long showId) {
        return remove(targets.forShow(showId));
    }

    /** @throws ThemeProblemException if there is no theme music */
    public Path movieThemeFile(long movieId) {
        return files.find(targets.forMovie(movieId)).orElseThrow(ThemeProblemException::themeMissing);
    }

    /** @throws ThemeProblemException if there is no theme music */
    public Path showThemeFile(long showId) {
        return files.find(targets.forShow(showId)).orElseThrow(ThemeProblemException::themeMissing);
    }

    /** @return whether a theme music was added; an existing one stays */
    public boolean fillMovie(long movieId) {
        return fillMissing(targets.forMovie(movieId));
    }

    /** @return whether a theme music was added; an existing one stays */
    public boolean fillShow(long showId) {
        return fillMissing(targets.forShow(showId));
    }

    private boolean fillMissing(ThemeTarget target) {
        if (files.find(target).isPresent()) {
            return false;
        }
        Optional<ThemeOption> match = ThemeFinder.safeMatch(finder.find(target));
        match.ifPresent(option -> apply(target, option));
        return match.isPresent();
    }

    private ThemeInfo apply(ThemeTarget target, ThemeOption option) {
        ThemeScraper scraper = scraperRegistry.themeScrapersFor(target.mediaType()).stream()
                .filter(candidate -> candidate.id().equals(option.scraperId()))
                .findFirst()
                .orElseThrow(() -> ThemeProblemException.unknownScraper(option.scraperId()));
        try {
            files.save(target, scraper, option.toCandidate());
        } catch (ScraperException e) {
            throw ThemeProblemException.downloadFailed(e);
        }
        log.info("Theme of \"{}\" saved from {}: {}", target.title(), scraper.id(), option.source());
        return files.info(target);
    }

    private ThemeInfo remove(ThemeTarget target) {
        files.delete(target);
        log.info("Theme of \"{}\" removed", target.title());
        return files.info(target);
    }
}
