package de.tiktak.kmm.movie;

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Eine vorhandene NFO-Datei und ihre letzte Änderungszeit. Die Zeit wird auf Mikrosekunden gekürzt,
 * weil die Datenbank nicht genauer speichert; sonst gälte jede NFO bei jedem Scan als geändert.
 */
record NfoFileState(Path file, Instant modifiedAt) {

    NfoFileState {
        Objects.requireNonNull(file, "file must not be null");
        modifiedAt = Objects.requireNonNull(modifiedAt, "modifiedAt must not be null").truncatedTo(ChronoUnit.MICROS);
    }
}
