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
}
