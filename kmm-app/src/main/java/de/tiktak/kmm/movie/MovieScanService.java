package de.tiktak.kmm.movie;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.springframework.stereotype.Service;

import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.scanner.MovieCandidate;
import de.tiktak.kmm.scanner.MovieDirectoryScanner;
import de.tiktak.kmm.source.MediaSource;
import de.tiktak.kmm.source.MediaSourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Durchsucht eine Filmquelle und übernimmt das Ergebnis in die Bibliothek. Das Dateisystem wird
 * außerhalb der Datenbanktransaktion gelesen, damit lange Scans keine Transaktion offen halten.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MovieScanService {

    private final MediaSourceService sourceService;
    private final MovieDirectoryScanner scanner;
    private final MovieLibrarySynchronizer synchronizer;

    public ScanResult scan(long sourceId) {
        MediaSource source = sourceService.require(sourceId);
        List<MovieCandidate> candidates = findCandidates(source);
        ScanResult result = synchronizer.synchronize(sourceId, candidates);
        log.info("Scanned source '{}': {} added, {} removed, {} unchanged",
                source.getName(), result.added(), result.removed(), result.unchanged());
        return result;
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
