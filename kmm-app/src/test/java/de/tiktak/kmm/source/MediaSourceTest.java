package de.tiktak.kmm.source;

import static org.assertj.core.api.Assertions.assertThatNullPointerException;

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
}
