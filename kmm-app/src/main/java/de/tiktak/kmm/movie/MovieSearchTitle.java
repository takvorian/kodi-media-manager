package de.tiktak.kmm.movie;

import de.tiktak.kmm.scanner.MovieNameParser;
import de.tiktak.kmm.scanner.ParsedMovieName;

/**
 * Der eigentliche Titel eines Films. Ein noch nicht zugeordneter Film heißt nach dem Scan wie sein Ordner, etwa
 * {@code Wonka (2023) tt6166392}; damit fände ein Anbieter nichts, und beim Umbenennen stünde das Jahr doppelt im Namen.
 */
final class MovieSearchTitle {

    private static final MovieNameParser PARSER = new MovieNameParser();

    private MovieSearchTitle() {
    }

    /** Der Titel ohne Jahr, ID und Release-Angaben; bei einem zugeordneten Film der Titel selbst. */
    static String of(MovieMetadata metadata) {
        if (metadata.tmdbId() != null) {
            return metadata.title();
        }
        ParsedMovieName parsed = PARSER.parse(metadata.title());
        boolean numberBelongsToTitle = parsed.year() != null && metadata.year() != null
                && !parsed.year().equals(metadata.year());
        return parsed.hasTitle() && !numberBelongsToTitle ? parsed.title() : metadata.title();
    }

    /** Das Jahr des Films, sonst das im Titel genannte. */
    static Integer yearOf(MovieMetadata metadata) {
        return metadata.year() != null ? metadata.year() : PARSER.parse(metadata.title()).year();
    }
}
