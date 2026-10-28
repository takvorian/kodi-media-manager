package de.tiktak.kmm.autoscrape;

import java.util.List;

/**
 * Ergebnis des automatischen Scrapens einer Quelle.
 *
 * @param movies          bearbeitete Filme
 * @param scraped         Filme, deren Metadaten neu geholt wurden
 * @param unmatched       Filme ohne eindeutigen Treffer
 * @param imagesSaved     neu gespeicherte Bilder
 * @param trailersSaved   neu eingetragene Trailer-Links
 * @param unmatchedTitles Titel der Filme ohne eindeutigen Treffer (gekürzt)
 * @param failures        Fehler je Film als „Titel: Meldung“ (gekürzt)
 * @param warnings        einmalige Hinweise, z. B. zu nicht eingerichteten Anbietern (gekürzt)
 */
public record AutoScrapeResult(
        int movies,
        int scraped,
        int unmatched,
        int imagesSaved,
        int trailersSaved,
        List<String> unmatchedTitles,
        List<String> failures,
        List<String> warnings) {

    public AutoScrapeResult {
        unmatchedTitles = List.copyOf(unmatchedTitles);
        failures = List.copyOf(failures);
        warnings = List.copyOf(warnings);
    }
}
