package de.tiktak.kmm.movie;

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * An existing NFO file and its last modification time. The time is truncated to microseconds
 * because the database does not store more precisely; otherwise every NFO would count as changed on every scan.
 */
record NfoFileState(Path file, Instant modifiedAt) {

    NfoFileState {
        Objects.requireNonNull(file, "file must not be null");
        modifiedAt = Objects.requireNonNull(modifiedAt, "modifiedAt must not be null").truncatedTo(ChronoUnit.MICROS);
    }
}
