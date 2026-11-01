package de.tiktak.kmm.autoscrape;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.source.ScanEntry;

/**
 * Sammelt das Ergebnis während des Laufs. Listen werden gekürzt, damit das Ergebnis im Job-Zustand
 * auch bei großen Bibliotheken klein bleibt.
 */
final class AutoScrapeProgress {

    static final int MAX_LISTED_ENTRIES = 100;

    private final int movies;
    private int scraped;
    private int unmatched;
    private int imagesSaved;
    private int trailersSaved;
    private int themesSaved;
    private int enriched;
    private final List<ScanEntry> unmatchedEntries = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    private final Set<String> warnings = new LinkedHashSet<>();

    AutoScrapeProgress(int movies) {
        this.movies = movies;
    }

    void scraped() {
        scraped++;
    }

    void unmatched(long movieId, String title) {
        unmatched++;
        addLimited(unmatchedEntries, new ScanEntry(MediaType.MOVIE, movieId, title));
    }

    void imagesSaved(int count) {
        imagesSaved += count;
    }

    void trailerSaved() {
        trailersSaved++;
    }

    void themeSaved() {
        themesSaved++;
    }

    void enriched() {
        enriched++;
    }

    void failed(String title, String message) {
        addLimited(failures, "%s: %s".formatted(title, message));
    }

    void warn(String warning) {
        if (warnings.size() < MAX_LISTED_ENTRIES) {
            warnings.add(warning);
        }
    }

    AutoScrapeResult toResult() {
        return new AutoScrapeResult(movies, scraped, unmatched, imagesSaved, trailersSaved, themesSaved,
                enriched, unmatchedEntries, failures,
                List.copyOf(warnings));
    }

    private static <T> void addLimited(List<T> entries, T entry) {
        if (entries.size() < MAX_LISTED_ENTRIES) {
            entries.add(entry);
        }
    }
}
