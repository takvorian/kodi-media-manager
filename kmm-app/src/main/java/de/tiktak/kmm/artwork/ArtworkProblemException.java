package de.tiktak.kmm.artwork;

import java.nio.file.Path;

import org.springframework.http.HttpStatus;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.plugin.api.ImageType;
import de.tiktak.kmm.web.LocalizedProblemException;

/**
 * An image cannot be searched, loaded or saved. The messages are worded for users.
 */
public class ArtworkProblemException extends LocalizedProblemException {

    /** What an image type is not managed for, see {@link #unsupportedTvType(ImageType, Target)}. */
    enum Target {
        MOVIE_SET("movieSet"),
        TV_SHOW("tvShow"),
        SEASON("season"),
        EPISODE("episode");

        private final LocalizedText text;

        Target(String key) {
            this.text = LocalizedText.of("artwork.target." + key);
        }
    }

    protected ArtworkProblemException(HttpStatus status, LocalizedText title, LocalizedText detail,
            Throwable cause) {
        super(status, title, detail, cause);
    }

    private ArtworkProblemException(HttpStatus status, String key, LocalizedText detail, Throwable cause) {
        this(status, LocalizedText.of(key + ".title"), detail, cause);
    }

    static ArtworkProblemException unsupportedType(ImageType type) {
        return new ArtworkProblemException(HttpStatus.BAD_REQUEST, "artwork.unsupportedType",
                LocalizedText.of("artwork.unsupportedType.detail").with("type", type), null);
    }

    static ArtworkProblemException missingIds() {
        return new ArtworkProblemException(HttpStatus.UNPROCESSABLE_CONTENT, "artwork.missingIds",
                LocalizedText.of("artwork.missingIds.detail"), null);
    }

    static ArtworkProblemException imageMissing(ImageType type) {
        return new ArtworkProblemException(HttpStatus.NOT_FOUND, "artwork.imageMissing",
                LocalizedText.of("artwork.imageMissing.detail").with("type", type), null);
    }

    static ArtworkProblemException unwritable(Path file, Throwable cause) {
        return new ArtworkProblemException(HttpStatus.UNPROCESSABLE_CONTENT, "artwork.unwritable",
                LocalizedText.of("artwork.unwritable.detail").with("file", file), cause);
    }

    static ArtworkProblemException unsupportedTvType(ImageType type, Target target) {
        return new ArtworkProblemException(HttpStatus.BAD_REQUEST, "artwork.unsupportedType",
                LocalizedText.of("artwork.unsupportedType.otherDetail")
                        .with("type", type)
                        .with("target", target.text), null);
    }

    static ArtworkProblemException missingCollectionId() {
        return new ArtworkProblemException(HttpStatus.UNPROCESSABLE_CONTENT, "artwork.missingCollectionId",
                LocalizedText.of("artwork.missingCollectionId.detail"), null);
    }

    static ArtworkProblemException noMovieSetImageStorage() {
        return new ArtworkProblemException(HttpStatus.UNPROCESSABLE_CONTENT, "artwork.noMovieSetImageStorage",
                LocalizedText.of("artwork.noMovieSetImageStorage.detail"), null);
    }

    static ArtworkProblemException missingTvIds() {
        return new ArtworkProblemException(HttpStatus.UNPROCESSABLE_CONTENT, "artwork.missingTvIds",
                LocalizedText.of("artwork.missingTvIds.detail"), null);
    }
}
