package de.tiktak.kmm.theme;

import java.nio.file.Path;

import org.springframework.http.HttpStatus;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.plugin.api.ScraperException;
import de.tiktak.kmm.web.LocalizedProblemException;

/**
 * Theme music cannot be searched, loaded or saved. The messages are worded for users.
 */
public class ThemeProblemException extends LocalizedProblemException {

    private ThemeProblemException(HttpStatus status, String key, LocalizedText detail, Throwable cause) {
        super(status, LocalizedText.of(key + ".title"), detail, cause);
    }

    static ThemeProblemException unknownScraper(String scraperId) {
        return new ThemeProblemException(HttpStatus.BAD_REQUEST, "theme.unknownScraper",
                LocalizedText.of("theme.unknownScraper.detail").with("id", scraperId), null);
    }

    static ThemeProblemException downloadFailed(ScraperException cause) {
        return new ThemeProblemException(HttpStatus.BAD_GATEWAY, "theme.downloadFailed", cause.localizedText(),
                cause);
    }

    static ThemeProblemException themeMissing() {
        return new ThemeProblemException(HttpStatus.NOT_FOUND, "theme.missing",
                LocalizedText.of("theme.missing.detail"), null);
    }

    static ThemeProblemException unwritable(Path file, Throwable cause) {
        return new ThemeProblemException(HttpStatus.UNPROCESSABLE_CONTENT, "theme.unwritable",
                LocalizedText.of("theme.unwritable.detail").with("file", file), cause);
    }
}
