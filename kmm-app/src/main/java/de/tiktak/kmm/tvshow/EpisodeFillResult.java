package de.tiktak.kmm.tvshow;

import java.util.List;

/**
 * Ergebnis der Übernahme von Episodendaten aus der Online-Suche.
 *
 * @param updated     Episoden, bei denen leere Angaben ergänzt wurden
 * @param unchanged   Episoden, die der Anbieter kennt, bei denen aber nichts fehlte
 * @param unmatched   Episoden der Bibliothek, die der Anbieter nicht kennt
 * @param failedFiles NFOs, die nicht geschrieben werden konnten; die Episoden dazu blieben unverändert
 */
public record EpisodeFillResult(int updated, int unchanged, int unmatched, List<String> failedFiles) {

    public EpisodeFillResult {
        failedFiles = List.copyOf(failedFiles);
    }
}
