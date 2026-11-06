package de.tiktak.kmm.migration;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import de.tiktak.kmm.i18n.LocalizedText;

/**
 * Collects the result of an import. The lists are limited so the report stays manageable even for large
 * libraries; the counters keep counting.
 */
final class EmberImportProgress {

    static final int MAX_LISTED_ENTRIES = 100;

    private final boolean dryRun;
    private int sourcesCreated;
    private int sourcesReused;
    private int moviesCreated;
    private int moviesUpdated;
    private int moviesSkipped;
    private int moviesWithoutNfo;
    private int showsCreated;
    private int showsUpdated;
    private int showsSkipped;
    private int episodesCreated;
    private int episodesUpdated;
    private int episodesSkipped;
    private int episodesMissing;
    private int kodiHostsCreated;
    private int kodiHostsUpdated;
    private final List<LocalizedText> settingsChanges = new ArrayList<>();
    private final List<LocalizedText> skippedSources = new ArrayList<>();
    private final List<String> missingFiles = new ArrayList<>();
    private final Set<LocalizedText> warnings = new LinkedHashSet<>();

    EmberImportProgress(boolean dryRun) {
        this.dryRun = dryRun;
    }

    void sourceImported(boolean created) {
        if (created) {
            sourcesCreated++;
        } else {
            sourcesReused++;
        }
    }

    void sourceSkipped(LocalizedText description) {
        addLimited(skippedSources, description);
    }

    void movieImported(boolean created) {
        if (created) {
            moviesCreated++;
        } else {
            moviesUpdated++;
        }
    }

    void movieSkipped() {
        moviesSkipped++;
    }

    void showImported(boolean created) {
        if (created) {
            showsCreated++;
        } else {
            showsUpdated++;
        }
    }

    void showSkipped() {
        showsSkipped++;
    }

    void episodeImported(boolean created) {
        if (created) {
            episodesCreated++;
        } else {
            episodesUpdated++;
        }
    }

    void episodeSkipped() {
        episodesSkipped++;
    }

    void episodesMissing(int count) {
        episodesMissing += count;
    }

    void kodiHostImported(boolean created) {
        if (created) {
            kodiHostsCreated++;
        } else {
            kodiHostsUpdated++;
        }
    }

    void nfoMissing(String movie) {
        moviesWithoutNfo++;
        addLimited(missingFiles, movie);
    }

    void warn(LocalizedText warning) {
        if (warnings.size() < MAX_LISTED_ENTRIES) {
            warnings.add(warning);
        }
    }

    void settingsChanged(List<LocalizedText> changes) {
        settingsChanges.addAll(changes);
    }

    EmberImportReport toReport() {
        return new EmberImportReport(dryRun, sourcesCreated, sourcesReused, moviesCreated, moviesUpdated,
                moviesSkipped, moviesWithoutNfo, new EmberImportReport.TvShows(showsCreated, showsUpdated,
                        showsSkipped, episodesCreated, episodesUpdated, episodesSkipped, episodesMissing),
                kodiHostsCreated, kodiHostsUpdated, settingsChanges, skippedSources, missingFiles,
                List.copyOf(warnings));
    }

    private static <T> void addLimited(List<T> list, T entry) {
        if (list.size() < MAX_LISTED_ENTRIES) {
            list.add(entry);
        }
    }
}
