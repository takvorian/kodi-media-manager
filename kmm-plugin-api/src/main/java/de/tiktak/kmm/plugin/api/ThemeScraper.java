package de.tiktak.kmm.plugin.api;

import java.nio.file.Path;
import java.util.List;

/**
 * Searches theme music for a movie or TV show and downloads it as an MP3 that KMM stores as {@code theme.mp3}. The
 * search uses the title ({@link ScrapeRequest#title()}).
 */
public interface ThemeScraper extends Scraper {

    /**
     * @return found theme music in the order of the provider; empty if nothing matches
     * @throws ScraperException if the provider cannot be reached or rejects the request
     */
    List<ThemeCandidate> findThemes(ScrapeRequest request);

    /**
     * Downloads the music of one of this scraper's candidates as an MP3 to {@code target}; an existing file is
     * replaced.
     *
     * @throws ScraperException if the candidate does not come from this scraper or cannot be loaded
     */
    void download(ThemeCandidate candidate, Path target);
}
