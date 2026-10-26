package de.tiktak.kmm.tvshow;

import java.util.Objects;
import java.util.Optional;

/**
 * Inhalt einer NFO, gelesen außerhalb der Datenbanktransaktion.
 *
 * @param state   die gelesene Datei und ihre Änderungszeit
 * @param content der Inhalt oder leer, wenn die Datei unlesbar ist
 */
record LoadedNfo<T>(TvNfoState state, Optional<T> content) {

    LoadedNfo {
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(content, "content must not be null");
    }

    /** Ist die NFO seit dem Lesen unverändert geblieben? */
    boolean isOf(TvNfoState nfo) {
        return state.equals(nfo);
    }
}
