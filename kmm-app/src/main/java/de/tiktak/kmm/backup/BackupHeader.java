package de.tiktak.kmm.backup;

import java.util.List;
import java.util.Map;

/**
 * Was eine Sicherungsdatei enthält, gelesen ohne ihre Zeilen.
 *
 * @param schemaVersion      Stand der Datenbank-Migrationen beim Export
 * @param applicationVersion KMM-Version beim Export
 * @param createdAt          Zeitpunkt des Exports
 * @param credentials        ob API-Keys und Passwörter enthalten sind
 * @param counts             Anzahl der Zeilen je Tabelle
 * @param sources            Quellen der Datei, ihre Pfade sind die Vorlage für die Pfad-Zuordnung
 */
public record BackupHeader(String schemaVersion, String applicationVersion, String createdAt, boolean credentials,
        Map<String, Integer> counts, List<Source> sources) {

    public record Source(String name, String path, String mediaType) {
    }
}
