package de.tiktak.kmm.movie;

import java.util.List;

/**
 * Ergebnis von „Alle Sets ergänzen“.
 *
 * @param overviews         Sets, die eine Beschreibung bekommen haben
 * @param images            gespeicherte Bilder
 * @param withoutCollection Sets ohne TMDB-ID, für die nichts gesucht wurde
 * @param problems          Fehler und Hinweise, höchstens {@link MovieSetJobService#MAX_LISTED_FAILURES}
 */
public record MovieSetCompletionResult(int overviews, int images, int withoutCollection, List<String> problems) {

    public MovieSetCompletionResult {
        problems = List.copyOf(problems);
    }
}
