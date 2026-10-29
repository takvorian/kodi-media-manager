package de.tiktak.kmm.autoscrape;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
    private final List<String> unmatchedTitles = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    private final Set<String> warnings = new LinkedHashSet<>();

    AutoScrapeProgress(int movies) {
        this.movies = movies;
    }

    void scraped() {
        scraped++;
    }

    void unmatched(String title) {
        unmatched++;
        addLimited(unmatchedTitles, title);
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
                unmatchedTitles, failures,
                List.copyOf(warnings));
    }

    private static void addLimited(List<String> entries, String entry) {
        if (entries.size() < MAX_LISTED_ENTRIES) {
            entries.add(entry);
        }
    }
}
