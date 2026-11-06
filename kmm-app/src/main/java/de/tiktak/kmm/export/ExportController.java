package de.tiktak.kmm.export;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import java.util.function.Supplier;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.i18n.Messages;
import de.tiktak.kmm.movie.MovieController;
import de.tiktak.kmm.movie.MovieFilter;
import de.tiktak.kmm.movie.MovieQueryService;
import de.tiktak.kmm.tvshow.TvShowController;
import de.tiktak.kmm.tvshow.TvShowFilter;
import de.tiktak.kmm.tvshow.TvShowQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Export of the movie and TV show list with the chosen filter as a download (CSV, JSON or HTML list as ZIP), in the
 * language of the user interface ({@code lang}, English if missing or unsupported).
 */
@RestController
@RequiredArgsConstructor
public class ExportController {

    public static final String MOVIES_EXPORT_PATH = MovieController.MOVIES_PATH + "/export";
    public static final String SHOWS_EXPORT_PATH = TvShowController.SHOWS_PATH + "/export";
    private static final String DEFAULT_LANGUAGE = "en";
    private static final LocalizedText MOVIES_FILE = LocalizedText.of("export.fileName.movies");
    private static final LocalizedText SHOWS_FILE = LocalizedText.of("export.fileName.tvShows");

    private final MovieQueryService movieQueries;
    private final TvShowQueryService showQueries;
    private final ExportRows rows;
    private final ExportWriter writer;
    private final Clock clock;

    @PostMapping(MOVIES_EXPORT_PATH)
    public ResponseEntity<Resource> exportMovies(@RequestParam ExportFormat format,
            @RequestParam(defaultValue = DEFAULT_LANGUAGE) String lang, @Valid @RequestBody MovieFilter filter)
            throws IOException {
        var movieIds = movieQueries.findMovieIds(filter);
        Locale locale = locale(lang);
        return download(Messages.render(MOVIES_FILE, locale), format,
                () -> rows.movies(movieIds, format == ExportFormat.HTML, clock.getZone(), locale));
    }

    @PostMapping(SHOWS_EXPORT_PATH)
    public ResponseEntity<Resource> exportShows(@RequestParam ExportFormat format,
            @RequestParam(defaultValue = DEFAULT_LANGUAGE) String lang, @Valid @RequestBody TvShowFilter filter)
            throws IOException {
        var showIds = showQueries.findShowIds(filter);
        Locale locale = locale(lang);
        return download(Messages.render(SHOWS_FILE, locale), format,
                () -> rows.shows(showIds, format == ExportFormat.HTML, clock.getZone(), locale));
    }

    /** Accepts {@code de}, {@code de-DE}, {@code de_DE} and the like; anything else becomes English. */
    private static Locale locale(String language) {
        return Messages.supported(Locale.forLanguageTag(language.replace('_', '-')));
    }

    /**
     * Writes the export to a temporary file first and then delivers it: the HTML list with many posters takes longer
     * than the time limit for answers produced in the background. The file is deleted after sending.
     */
    private ResponseEntity<Resource> download(String name, ExportFormat format, Supplier<ExportTable> table)
            throws IOException {
        LocalDate today = LocalDate.now(clock);
        String fileName = "%s-%s.%s".formatted(name, today, format.extension());
        Path file = Files.createTempFile("kmm-export-", "." + format.extension());
        try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(file))) {
            writer.write(table.get(), format, today, output);
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(file);
            throw e;
        }
        return ResponseEntity.ok()
                .contentType(format.mediaType())
                .contentLength(Files.size(file))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build()
                        .toString())
                .body(new TemporaryFileResource(file));
    }
}
