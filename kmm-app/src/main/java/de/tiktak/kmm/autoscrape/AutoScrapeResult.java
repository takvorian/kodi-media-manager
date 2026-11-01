package de.tiktak.kmm.autoscrape;

import java.util.List;

import de.tiktak.kmm.source.ScanEntry;

/**
 * Ergebnis des automatischen Scrapens einer Filmquelle oder einer Auswahl von Filmen.
 *
 * @param movies          bearbeitete Filme
 * @param scraped         Filme, deren Metadaten neu geholt wurden
 * @param unmatched       Filme ohne eindeutigen Treffer
 * @param imagesSaved     neu gespeicherte Bilder
 * @param trailersSaved   neu eingetragene Trailer-Links
 * @param themesSaved     neu gespeicherte Titelmusik ({@code theme.mp3})
 * @param enriched        Filme mit ergänzten Bewertungen oder Feldern (etwa Trakt, OMDb)
 * @param unmatchedEntries Filme ohne eindeutigen Treffer (gekürzt)
 * @param failures        Fehler je Film als „Titel: Meldung“ (gekürzt)
 * @param warnings        einmalige Hinweise, z. B. zu nicht eingerichteten Anbietern (gekürzt)
 */
public record AutoScrapeResult(
        int movies,
        int scraped,
        int unmatched,
        int imagesSaved,
        int trailersSaved,
        int themesSaved,
        int enriched,
        List<ScanEntry> unmatchedEntries,
        List<String> failures,
        List<String> warnings) {

    public AutoScrapeResult {
        unmatchedEntries = List.copyOf(unmatchedEntries);
        failures = List.copyOf(failures);
        warnings = List.copyOf(warnings);
    }
}
