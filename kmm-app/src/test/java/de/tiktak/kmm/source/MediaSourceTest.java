package de.tiktak.kmm.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import de.tiktak.kmm.plugin.api.MediaType;

class MediaSourceTest {

    @Test
    void rejectsMissingName() {
        assertThatNullPointerException()
                .isThrownBy(() -> new MediaSource(null, "/media/filme", MediaType.MOVIE))
                .withMessageContaining("name");
    }

    @Test
    void rejectsMissingPath() {
        assertThatNullPointerException()
                .isThrownBy(() -> new MediaSource("Filme", null, MediaType.MOVIE))
                .withMessageContaining("path");
    }

    @Test
    void rejectsMissingMediaType() {
        assertThatNullPointerException()
                .isThrownBy(() -> new MediaSource("Filme", "/media/filme", null))
                .withMessageContaining("mediaType");
    }

    @Test
    void remembersScanTime() {
        var source = new MediaSource("Filme", "/media/filme", MediaType.MOVIE);
        Instant scannedAt = Instant.parse("2026-10-25T10:15:30Z");

        source.markScanned(scannedAt);

        assertThat(source.getLastScannedAt()).isEqualTo(scannedAt);
    }

    @Test
    void rejectsMissingScanTime() {
        var source = new MediaSource("Filme", "/media/filme", MediaType.MOVIE);

        assertThatNullPointerException().isThrownBy(() -> source.markScanned(null)).withMessageContaining("scannedAt");
    }
}
