package de.tiktak.kmm.movie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.nio.file.Path;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class NfoFileStateTest {

    private static final Path NFO = Path.of("Heat.nfo");

    @Test
    void truncatesModificationTimeToDatabasePrecision() {
        var state = new NfoFileState(NFO, Instant.parse("2026-10-25T12:00:00.123456789Z"));

        assertThat(state.modifiedAt()).isEqualTo(Instant.parse("2026-10-25T12:00:00.123456Z"));
    }

    @Test
    void rejectsMissingValues() {
        assertThatNullPointerException().isThrownBy(() -> new NfoFileState(null, Instant.EPOCH));
        assertThatNullPointerException().isThrownBy(() -> new NfoFileState(NFO, null));
    }
}
