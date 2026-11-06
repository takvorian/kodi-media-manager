package de.tiktak.kmm.movie;

import org.springframework.http.HttpStatus;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.source.MediaSource;
import de.tiktak.kmm.web.LocalizedProblemException;

/**
 * A source cannot be searched for movies. Answered as HTTP 422.
 */
public class SourceNotScannableException extends LocalizedProblemException {

    private static final LocalizedText TITLE = LocalizedText.of("movie.scan.notScannable.title");

    private SourceNotScannableException(LocalizedText detail) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, TITLE, detail, null);
    }

    static SourceNotScannableException notAMovieSource(MediaSource source) {
        return new SourceNotScannableException(LocalizedText.of("movie.scan.notAMovieSource.detail")
                .with("source", source.getName())
                .with("mediaType", source.getMediaType()));
    }

    static SourceNotScannableException unreachable(MediaSource source) {
        return new SourceNotScannableException(LocalizedText.of("movie.scan.unreachable.detail")
                .with("path", source.getPath())
                .with("source", source.getName()));
    }
}
