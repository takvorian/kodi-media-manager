package de.tiktak.kmm.plugin.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.net.URI;

import org.junit.jupiter.api.Test;

class ImageCandidateTest {

    private static final URI POSTER_URL = URI.create("https://image.example.org/poster.jpg");

    @Test
    void createsCandidateWithOptionalValuesMissing() {
        var candidate = new ImageCandidate(ImageType.POSTER, POSTER_URL, null, null, null);

        assertThat(candidate.type()).isEqualTo(ImageType.POSTER);
        assertThat(candidate.url()).isEqualTo(POSTER_URL);
        assertThat(candidate.language()).isNull();
    }

    @Test
    void rejectsMissingType() {
        assertThatNullPointerException()
                .isThrownBy(() -> new ImageCandidate(null, POSTER_URL, 1000, 1500, "de"))
                .withMessageContaining("type");
    }

    @Test
    void rejectsMissingUrl() {
        assertThatNullPointerException()
                .isThrownBy(() -> new ImageCandidate(ImageType.FANART, null, 1920, 1080, null))
                .withMessageContaining("url");
    }
}
