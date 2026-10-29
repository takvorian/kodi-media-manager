package de.tiktak.kmm.export;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.function.Supplier;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tiktak.kmm.movie.MovieController;
import de.tiktak.kmm.movie.MovieFilter;
import de.tiktak.kmm.movie.MovieQueryService;
import de.tiktak.kmm.tvshow.TvShowController;
import de.tiktak.kmm.tvshow.TvShowFilter;
import de.tiktak.kmm.tvshow.TvShowQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Export der Film- und Serienliste mit dem gewählten Filter als Download (CSV, JSON oder HTML-Liste als ZIP).
 */
@RestController
@RequiredArgsConstructor
public class ExportController {

    public static final String MOVIES_EXPORT_PATH = MovieController.MOVIES_PATH + "/export";
    public static final String SHOWS_EXPORT_PATH = TvShowController.SHOWS_PATH + "/export";

    private final MovieQueryService movieQueries;
    private final TvShowQueryService showQueries;
    private final ExportRows rows;
    private final ExportWriter writer;
    private final Clock clock;

    @PostMapping(MOVIES_EXPORT_PATH)
    public ResponseEntity<Resource> exportMovies(@RequestParam ExportFormat format,
            @Valid @RequestBody MovieFilter filter) throws IOException {
        var movieIds = movieQueries.findMovieIds(filter);
        return download("kmm-filme", format,
                () -> rows.movies(movieIds, format == ExportFormat.HTML, clock.getZone()));
    }

    @PostMapping(SHOWS_EXPORT_PATH)
    public ResponseEntity<Resource> exportShows(@RequestParam ExportFormat format,
            @Valid @RequestBody TvShowFilter filter) throws IOException {
        var showIds = showQueries.findShowIds(filter);
        return download("kmm-serien", format,
                () -> rows.shows(showIds, format == ExportFormat.HTML, clock.getZone()));
    }

    /**
     * Schreibt den Export erst in eine temporäre Datei und liefert sie dann aus: Die HTML-Liste mit vielen Postern dauert
     * länger als das Zeitlimit für Antworten, die im Hintergrund entstehen. Die Datei wird nach dem Senden gelöscht.
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
