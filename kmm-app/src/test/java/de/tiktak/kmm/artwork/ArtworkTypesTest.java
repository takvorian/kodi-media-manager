package de.tiktak.kmm.artwork;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tiktak.kmm.plugin.api.ImageType;

class ArtworkTypesTest {

    @Test
    void usesKodiNamesForFiles() {
        assertThat(ArtworkTypes.kodiName(ImageType.POSTER)).isEqualTo("poster");
        assertThat(ArtworkTypes.kodiName(ImageType.CLEARLOGO)).isEqualTo("clearlogo");
    }

    @Test
    void managesTheCommonKodiMovieArtwork() {
        assertThat(ArtworkTypes.MOVIE_TYPES).containsExactly(ImageType.POSTER, ImageType.FANART, ImageType.CLEARLOGO,
                ImageType.CLEARART, ImageType.DISCART, ImageType.BANNER, ImageType.LANDSCAPE);
    }
}
