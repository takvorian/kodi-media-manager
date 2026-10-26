package de.tiktak.kmm.tvshow;

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Eine vorhandene NFO-Datei und ihre letzte Änderungszeit. Die Zeit wird auf Mikrosekunden gekürzt,
 * weil die Datenbank nicht genauer speichert; sonst gälte jede NFO bei jedem Scan als geändert.
 */
record TvNfoState(Path file, Instant modifiedAt) {

    TvNfoState {
        Objects.requireNonNull(file, "file must not be null");
        modifiedAt = Objects.requireNonNull(modifiedAt, "modifiedAt must not be null").truncatedTo(ChronoUnit.MICROS);
    }
}
