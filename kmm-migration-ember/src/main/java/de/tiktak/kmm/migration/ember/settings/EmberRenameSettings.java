package de.tiktak.kmm.migration.ember.settings;

/**
 * Einstellungen von Embers Umbenenner (Addon {@code generic.EmberCore.BulkRename}) in Embers Muster-Syntax,
 * etwa {@code $T {($Y)}}. Nicht gesetzte Werte sind {@code null}.
 *
 * @param movieFolder    Ordner eines Films
 * @param movieFile      Dateien eines Films
 * @param tvShowFolder   Ordner einer Serie
 * @param seasonFolder   Ordner einer Staffel
 * @param episodeFile    Dateien einer Episode
 * @param movieOnEdit    Filme nach dem Bearbeiten umbenennen
 * @param movieOnScrape  Filme nach dem Scrapen umbenennen (einzeln oder mehrere)
 * @param tvOnEdit       Episoden nach dem Bearbeiten umbenennen
 * @param tvOnScrape     Serien und Episoden nach dem Scrapen umbenennen
 * @param episodesOnScan neue Episoden beim Scan umbenennen
 */
public record EmberRenameSettings(
        String movieFolder,
        String movieFile,
        String tvShowFolder,
        String seasonFolder,
        String episodeFile,
        Boolean movieOnEdit,
        Boolean movieOnScrape,
        Boolean tvOnEdit,
        Boolean tvOnScrape,
        Boolean episodesOnScan) {
}
