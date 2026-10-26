package de.tiktak.kmm.migration;

import java.util.List;

/**
 * Ergebnis eines Ember-Imports bzw. Probelaufs.
 *
 * @param dryRun           ob nichts gespeichert wurde
 * @param sourcesCreated   neu angelegte Quellen
 * @param sourcesReused    vorhandene Quellen mit demselben Verzeichnis
 * @param moviesCreated    neu angelegte Filme
 * @param moviesUpdated    vorhandene Filme, deren Angaben aus Ember übernommen wurden
 * @param moviesSkipped    Filme ohne Pfad-Zuordnung oder ohne übernommene Quelle
 * @param moviesWithoutNfo Filme, deren Datei oder NFO KMM nicht gefunden hat
 * @param tvShows          Ergebnis für Serien und Episoden
 * @param kodiHostsCreated neu angelegte Kodi-Geräte
 * @param kodiHostsUpdated vorhandene Kodi-Geräte mit demselben Namen, die aktualisiert wurden
 * @param settingsChanges  übernommene Einstellungen in lesbarer Form
 * @param skippedSources   Quellen, die nicht übernommen wurden, mit Grund
 * @param missingFiles     Filme ohne gefundene NFO (gekürzt)
 * @param warnings         weitere Hinweise (gekürzt)
 */
public record EmberImportReport(
        boolean dryRun,
        int sourcesCreated,
        int sourcesReused,
        int moviesCreated,
        int moviesUpdated,
        int moviesSkipped,
        int moviesWithoutNfo,
        TvShows tvShows,
        int kodiHostsCreated,
        int kodiHostsUpdated,
        List<String> settingsChanges,
        List<String> skippedSources,
        List<String> missingFiles,
        List<String> warnings) {

    public EmberImportReport {
        settingsChanges = List.copyOf(settingsChanges);
        skippedSources = List.copyOf(skippedSources);
        missingFiles = List.copyOf(missingFiles);
        warnings = List.copyOf(warnings);
    }

    /**
     * @param showsSkipped    Serien ohne Pfad-Zuordnung oder ohne übernommene Quelle
     * @param episodesUpdated vorhandene Episoden, deren Angaben aus Ember übernommen wurden
     * @param episodesSkipped Episoden ohne Pfad-Zuordnung oder einer nicht übernommenen Serie
     * @param episodesMissing Episoden, die Ember ohne Datei kennt; sie werden nicht übernommen
     */
    public record TvShows(int showsCreated, int showsUpdated, int showsSkipped, int episodesCreated,
            int episodesUpdated, int episodesSkipped, int episodesMissing) {
    }
}
