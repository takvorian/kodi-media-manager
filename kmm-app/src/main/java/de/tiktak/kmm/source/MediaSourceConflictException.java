package de.tiktak.kmm.source;

import org.springframework.http.HttpStatus;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.web.LocalizedProblemException;

/**
 * A new source collides with an existing one. Answered as HTTP 409.
 */
public class MediaSourceConflictException extends LocalizedProblemException {

    private static final LocalizedText TITLE = LocalizedText.of("source.conflict.title");

    private MediaSourceConflictException(LocalizedText detail) {
        super(HttpStatus.CONFLICT, TITLE, detail, null);
    }

    static MediaSourceConflictException nameTaken(String name) {
        return new MediaSourceConflictException(LocalizedText.of("source.conflict.nameTaken").with("name", name));
    }

    /**
     * Overlapping sources would cover the same files twice.
     */
    static MediaSourceConflictException overlaps(String path, MediaSource existing) {
        return new MediaSourceConflictException(LocalizedText.of("source.conflict.overlaps")
                .with("path", path)
                .with("source", existing.getName())
                .with("sourcePath", existing.getPath()));
    }
}
