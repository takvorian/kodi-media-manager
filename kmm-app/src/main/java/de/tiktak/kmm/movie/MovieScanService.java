package de.tiktak.kmm.movie;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.springframework.stereotype.Service;

import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.scanner.CompanionFiles;
import de.tiktak.kmm.scanner.MovieCandidate;
import de.tiktak.kmm.scanner.MovieDirectoryScanner;
import de.tiktak.kmm.source.MediaSource;
import de.tiktak.kmm.source.MediaSourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Durchsucht eine Filmquelle und übernimmt das Ergebnis in die Bibliothek. Verzeichnisse und
 * NFO-Zeitstempel werden außerhalb der Datenbanktransaktion gelesen, damit lange Scans keine
 * Transaktion offen halten.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MovieScanService {

    private final MediaSourceService sourceService;
    private final MovieDirectoryScanner scanner;
    private final MovieLibrarySynchronizer synchronizer;
    private final MovieNfoFiles nfoFiles;

    public ScanResult scan(long sourceId) {
        MediaSource source = sourceService.require(sourceId);
        List<ScannedMovie> scannedMovies = findCandidates(source).stream().map(this::withNfo).toList();
        ScanResult result = synchronizer.synchronize(sourceId, scannedMovies);
        log.info("Scanned source '{}': {} added, {} updated, {} removed, {} unchanged, {} NFO errors",
                source.getName(), result.added(), result.updated(), result.removed(), result.unchanged(),
                result.nfoErrors());
        return result;
    }

    private ScannedMovie withNfo(MovieCandidate candidate) {
        CompanionFiles companionFiles = CompanionFiles.of(candidate.file(), candidate.format(), candidate.partCount());
        return new ScannedMovie(candidate, nfoFiles.find(companionFiles));
    }

    private List<MovieCandidate> findCandidates(MediaSource source) {
        if (source.getMediaType() != MediaType.MOVIE) {
            throw SourceNotScannableException.notAMovieSource(source);
        }
        Path directory = Path.of(source.getPath());
        if (!Files.isDirectory(directory)) {
            throw SourceNotScannableException.unreachable(source);
        }
        try {
            return scanner.scan(directory);
        } catch (UncheckedIOException e) {
            log.warn("Scanning source '{}' failed", source.getName(), e);
            throw SourceNotScannableException.unreachable(source);
        }
    }
}
