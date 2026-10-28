package de.tiktak.kmm.movie;

import java.util.List;

/**
 * Ergebnis des Umbenennens mehrerer Filme.
 *
 * @param renamed   umbenannte Filme
 * @param unchanged Filme, die schon so hießen
 * @param skipped   Filme mit Konflikt oder ohne gültigen Namen
 * @param failed    Filme, bei denen das Dateisystem das Umbenennen ablehnte
 * @param problems  Meldungen zu übersprungenen und fehlgeschlagenen Filmen, höchstens
 *                  {@link MovieRenameService#MAX_LISTED_PROBLEMS}
 */
public record MovieRenameResult(long sourceId, int renamed, int unchanged, int skipped, int failed,
                                List<String> problems) {

    public MovieRenameResult {
        problems = List.copyOf(problems);
    }
}
