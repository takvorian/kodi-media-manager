package de.tiktak.kmm.tvshow;

import org.springframework.http.HttpStatus;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.scanner.naming.NamePatternException;
import de.tiktak.kmm.source.MediaSource;
import de.tiktak.kmm.web.LocalizedProblemException;
import de.tiktak.kmm.web.LocalizedTexts;

/**
 * TV shows cannot be renamed like this. Answered as HTTP 422.
 */
public class TvRenameProblemException extends LocalizedProblemException {

    private static final LocalizedText TITLE = LocalizedText.of("tvShow.rename.title");

    private TvRenameProblemException(LocalizedText detail) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, TITLE, detail, null);
    }

    /**
     * @param label name of the pattern, e.g. "season folders"
     */
    static TvRenameProblemException invalidPattern(LocalizedText label, NamePatternException e) {
        return new TvRenameProblemException(LocalizedText.of("tvShow.rename.invalidPattern").with("pattern", label)
                .with("reason", LocalizedTexts.of(e)));
    }

    static TvRenameProblemException foldersNotAllowed(LocalizedText label) {
        return new TvRenameProblemException(
                LocalizedText.of("tvShow.rename.foldersNotAllowed").with("pattern", label));
    }

    static TvRenameProblemException notATvSource(MediaSource source) {
        return new TvRenameProblemException(
                LocalizedText.of("tvShow.rename.notATvSource").with("source", source.getName()));
    }
}
