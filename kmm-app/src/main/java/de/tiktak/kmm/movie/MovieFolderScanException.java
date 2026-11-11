package de.tiktak.kmm.movie;

import java.nio.file.Path;

import org.springframework.http.HttpStatus;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.web.LocalizedProblemException;

/**
 * The folder of a movie cannot be scanned. Nothing is removed from the library then, so that a drive that is
 * briefly unreachable costs no data.
 */
public class MovieFolderScanException extends LocalizedProblemException {

    private static final LocalizedText TITLE = LocalizedText.of("movie.folderScan.title");

    private MovieFolderScanException(LocalizedText detail) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, TITLE, detail, null);
    }

    static MovieFolderScanException unreachable(Movie movie, Path directory) {
        return new MovieFolderScanException(LocalizedText.of("movie.folderScan.unreachable")
                .with("title", movie.getTitle())
                .with("path", directory.toString()));
    }

    static MovieFolderScanException noVideoFiles(Movie movie, Path directory) {
        return new MovieFolderScanException(LocalizedText.of("movie.folderScan.noVideoFiles")
                .with("title", movie.getTitle())
                .with("path", directory.toString()));
    }
}
