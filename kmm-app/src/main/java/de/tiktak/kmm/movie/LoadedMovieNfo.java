package de.tiktak.kmm.movie;

import java.util.function.Supplier;

import de.tiktak.kmm.nfo.MovieNfo;
import de.tiktak.kmm.nfo.NfoException;

/**
 * Inhalt einer NFO, gelesen außerhalb der Datenbanktransaktion, oder der Fehler beim Lesen.
 */
final class LoadedMovieNfo {

    private final MovieNfo content;
    private final NfoException failure;

    private LoadedMovieNfo(MovieNfo content, NfoException failure) {
        this.content = content;
        this.failure = failure;
    }

    static LoadedMovieNfo read(Supplier<MovieNfo> reader) {
        try {
            return new LoadedMovieNfo(reader.get(), null);
        } catch (NfoException e) {
            return new LoadedMovieNfo(null, e);
        }
    }

    /**
     * @throws NfoException wenn die Datei beim Lesen unlesbar war
     */
    MovieNfo content() {
        if (failure != null) {
            throw failure;
        }
        return content;
    }
}
