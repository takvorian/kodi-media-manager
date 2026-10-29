package de.tiktak.kmm.autoscrape;

import java.util.List;

/**
 * Ergebnis des automatischen Scrapens einer Serienquelle.
 *
 * @param shows           bearbeitete Serien
 * @param scraped         Serien, die neu zugeordnet wurden
 * @param unmatched       Serien ohne eindeutigen Treffer
 * @param episodesUpdated Episoden, bei denen leere Angaben ergänzt wurden
 * @param imagesSaved     neu gespeicherte Bilder (Serie, Staffeln, Standbilder)
 * @param trailersSaved   neu eingetragene Trailer-Links
 * @param themesSaved     neu gespeicherte Titelmusik ({@code theme.mp3})
 * @param unmatchedTitles Titel der Serien ohne eindeutigen Treffer (gekürzt)
 * @param failures        Fehler je Serie als „Titel: Meldung“ (gekürzt)
 * @param warnings        einmalige Hinweise, z. B. zu nicht eingerichteten Anbietern (gekürzt)
 */
public record TvAutoScrapeResult(
        int shows,
        int scraped,
        int unmatched,
        int episodesUpdated,
        int imagesSaved,
        int trailersSaved,
        int themesSaved,
        List<String> unmatchedTitles,
        List<String> failures,
        List<String> warnings) {

    public TvAutoScrapeResult {
        unmatchedTitles = List.copyOf(unmatchedTitles);
        failures = List.copyOf(failures);
        warnings = List.copyOf(warnings);
    }
}
