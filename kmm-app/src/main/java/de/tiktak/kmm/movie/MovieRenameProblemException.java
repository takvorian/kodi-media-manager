package de.tiktak.kmm.movie;

import org.springframework.http.HttpStatus;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.scanner.naming.NamePatternException;
import de.tiktak.kmm.source.MediaSource;
import de.tiktak.kmm.web.LocalizedProblemException;

/**
 * Renaming is not possible like this. Answered as HTTP 422.
 */
public class MovieRenameProblemException extends LocalizedProblemException {

    private static final LocalizedText TITLE = LocalizedText.of("movie.rename.impossible.title");

    private MovieRenameProblemException(LocalizedText detail) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, TITLE, detail, null);
    }

    /** @param pattern which pattern is invalid, e.g. {@code movie.rename.pattern.folder} */
    static MovieRenameProblemException invalidPattern(LocalizedText pattern, NamePatternException e) {
        return new MovieRenameProblemException(LocalizedText.of("movie.rename.invalidPattern.detail")
                .with("pattern", pattern)
                .with("reason", e.localizedText()));
    }

    static MovieRenameProblemException folderInFilePattern() {
        return new MovieRenameProblemException(LocalizedText.of("movie.rename.folderInFilePattern.detail"));
    }

    static MovieRenameProblemException notAMovieSource(MediaSource source) {
        return new MovieRenameProblemException(LocalizedText.of("movie.rename.notAMovieSource.detail")
                .with("source", source.getName()));
    }
}
