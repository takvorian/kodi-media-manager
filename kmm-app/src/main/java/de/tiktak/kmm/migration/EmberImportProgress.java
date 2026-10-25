package de.tiktak.kmm.migration;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Sammelt das Ergebnis eines Imports. Die Listen sind begrenzt, damit der Bericht auch bei großen
 * Bibliotheken handlich bleibt; die Zähler zählen weiter.
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
    private int kodiHostsCreated;
    private int kodiHostsUpdated;
    private final List<String> settingsChanges = new ArrayList<>();
    private final List<String> skippedSources = new ArrayList<>();
    private final List<String> missingFiles = new ArrayList<>();
    private final Set<String> warnings = new LinkedHashSet<>();

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

    void sourceSkipped(String description) {
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

    void warn(String warning) {
        if (warnings.size() < MAX_LISTED_ENTRIES) {
            warnings.add(warning);
        }
    }

    void settingsChanged(List<String> changes) {
        settingsChanges.addAll(changes);
    }

    EmberImportReport toReport() {
        return new EmberImportReport(dryRun, sourcesCreated, sourcesReused, moviesCreated, moviesUpdated,
                moviesSkipped, moviesWithoutNfo, kodiHostsCreated, kodiHostsUpdated, settingsChanges, skippedSources, missingFiles, List.copyOf(warnings));
    }

    private static void addLimited(List<String> list, String entry) {
        if (list.size() < MAX_LISTED_ENTRIES) {
            list.add(entry);
        }
    }
}
