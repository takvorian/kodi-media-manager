package de.tiktak.kmm.plugin.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.net.URI;

import org.junit.jupiter.api.Test;

class ImageCandidateTest {

    private static final URI POSTER_URL = URI.create("https://image.example.org/poster.jpg");
    private static final URI PREVIEW_URL = URI.create("https://image.example.org/preview/poster.jpg");

    @Test
    void usesFullImageAsPreviewWhenNoneIsGiven() {
        var candidate = ImageCandidate.builder().type(ImageType.POSTER).url(POSTER_URL).build();
        assertThat(candidate.season()).as("without a season the image applies to the whole medium").isNull();

        assertThat(candidate.previewUrl()).isEqualTo(POSTER_URL);
        assertThat(candidate.language()).isNull();
        assertThat(candidate.rating()).isNull();
    }

    @Test
    void keepsAllValues() {
        var candidate = ImageCandidate.builder().type(ImageType.FANART).url(POSTER_URL).previewUrl(PREVIEW_URL)
                .width(1920).height(1080).language("de").rating(7.5).season(2).build();

        assertThat(candidate.previewUrl()).isEqualTo(PREVIEW_URL);
        assertThat(candidate.season()).isEqualTo(2);
        assertThat(candidate.width()).isEqualTo(1920);
        assertThat(candidate.rating()).isEqualTo(7.5);
    }

    @Test
    void rejectsMissingType() {
        assertThatNullPointerException()
                .isThrownBy(() -> ImageCandidate.builder().url(POSTER_URL).build())
                .withMessageContaining("type");
    }

    @Test
    void rejectsMissingUrl() {
        assertThatNullPointerException()
                .isThrownBy(() -> ImageCandidate.builder().type(ImageType.FANART).build())
                .withMessageContaining("url");
    }
}
