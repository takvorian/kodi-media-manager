package de.tiktak.kmm.tvshow;

import java.util.List;

/**
 * Ergebnis des Umbenennens mehrerer Serien.
 *
 * @param renamed   umbenannte Serien
 * @param unchanged Serien, die schon so hießen
 * @param skipped   Serien mit Konflikt oder ohne gültigen Namen
 * @param failed    Serien, bei denen das Dateisystem das Umbenennen ablehnte
 * @param problems  Meldungen zu übersprungenen Serien und Episodendateien, höchstens
 *                  {@link TvShowRenameService#MAX_LISTED_PROBLEMS}
 */
public record TvRenameResult(long sourceId, int renamed, int unchanged, int skipped, int failed,
                             List<String> problems) {

    public TvRenameResult {
        problems = List.copyOf(problems);
    }
}
