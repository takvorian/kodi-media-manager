package de.tiktak.kmm.movie;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import de.tiktak.kmm.scanner.MovieCandidate;
import de.tiktak.kmm.source.MediaSourceService;
import lombok.RequiredArgsConstructor;

/**
 * Gleicht die Filme einer Quelle in der Datenbank mit dem Scan-Ergebnis ab: Neue Dateien werden
 * angelegt, verschwundene entfernt. Bekannte Filme bleiben unverändert, damit gepflegte
 * Metadaten nicht überschrieben werden.
 */
@Component
@RequiredArgsConstructor
class MovieLibrarySynchronizer {

    private final MovieRepository movieRepository;
    private final MediaSourceService sourceService;
    private final Clock clock;

    @Transactional
    ScanResult synchronize(long sourceId, List<MovieCandidate> candidates) {
        Map<String, Movie> known = movieRepository.findBySourceId(sourceId).stream()
                .collect(Collectors.toMap(Movie::getFilePath, Function.identity()));
        Set<String> found = candidates.stream().map(MovieLibrarySynchronizer::pathOf).collect(Collectors.toSet());

        List<Movie> added = candidates.stream()
                .filter(candidate -> !known.containsKey(pathOf(candidate)))
                .map(candidate -> toMovie(sourceId, candidate))
                .toList();
        List<Movie> removed = known.values().stream()
                .filter(movie -> !found.contains(movie.getFilePath()))
                .toList();

        movieRepository.saveAll(added);
        movieRepository.deleteAll(removed);
        sourceService.markScanned(sourceId, clock.instant());
        return new ScanResult(sourceId, added.size(), removed.size(), unchangedCount(known.values(), removed));
    }

    private static int unchangedCount(Collection<Movie> known, List<Movie> removed) {
        return known.size() - removed.size();
    }

    private static Movie toMovie(long sourceId, MovieCandidate candidate) {
        return new Movie(sourceId, pathOf(candidate), candidate.title(), candidate.year(), candidate.format(),
                candidate.partCount());
    }

    private static String pathOf(MovieCandidate candidate) {
        return candidate.file().toAbsolutePath().normalize().toString();
    }
}
