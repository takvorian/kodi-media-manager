package de.tiktak.kmm.tvshow;

import java.util.Objects;
import java.util.Optional;

/**
 * Content of an NFO, read outside the database transaction.
 *
 * @param state   the file read and its modification time
 * @param content the content, or empty if the file is unreadable
 */
record LoadedNfo<T>(TvNfoState state, Optional<T> content) {

    LoadedNfo {
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(content, "content must not be null");
    }

    /** Has the NFO remained unchanged since it was read? */
    boolean isOf(TvNfoState nfo) {
        return state.equals(nfo);
    }
}
