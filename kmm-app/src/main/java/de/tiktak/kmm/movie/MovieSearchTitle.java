package de.tiktak.kmm.movie;

import de.tiktak.kmm.scanner.MovieNameParser;
import de.tiktak.kmm.scanner.ParsedMovieName;

/**
 * The actual title of a movie. A movie not yet matched is named after its folder following the scan, e.g.
 * {@code Wonka (2023) tt6166392}; a provider would find nothing with that, and renaming would put the year in the
 * name twice.
 */
final class MovieSearchTitle {

    private static final MovieNameParser PARSER = new MovieNameParser();

    private MovieSearchTitle() {
    }

    /** The title without year, ID and release details; for a matched movie, the title itself. */
    static String of(MovieMetadata metadata) {
        if (metadata.tmdbId() != null) {
            return metadata.title();
        }
        ParsedMovieName parsed = PARSER.parse(metadata.title());
        boolean numberBelongsToTitle = parsed.year() != null && metadata.year() != null
                && !parsed.year().equals(metadata.year());
        return parsed.hasTitle() && !numberBelongsToTitle ? parsed.title() : metadata.title();
    }

    /** The movie's year, otherwise the one named in the title. */
    static Integer yearOf(MovieMetadata metadata) {
        return metadata.year() != null ? metadata.year() : PARSER.parse(metadata.title()).year();
    }
}
