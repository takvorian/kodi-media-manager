package de.tiktak.kmm.movie;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import de.tiktak.kmm.movie.MovieRenamePlan.Status;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.scanner.CompanionFiles;
import de.tiktak.kmm.source.MediaSource;
import de.tiktak.kmm.source.MediaSourceService;
import lombok.RequiredArgsConstructor;

/**
 * Plant das Umbenennen der Filme einer Quelle oder eines einzelnen Films, ohne etwas zu ändern.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
class MovieRenamePlanning {

    private final MediaSourceService sourceService;
    private final MovieRepository movieRepository;

    /**
     * @return je Film der Quelle der Plan, sortiert nach Ordner
     * @throws MovieRenameProblemException wenn die Quelle keine Filme enthält
     */
    Planned planSource(long sourceId, MovieRenamePatterns.Parsed patterns) {
        MediaSource source = movieSource(sourceId);
        List<Movie> movies = movieRepository.findBySourceId(sourceId);
        Folders folders = Folders.of(movies, Path.of(source.getPath()));
        List<MovieRenamePlan> plans = movies.stream()
                .map(movie -> MovieRenamePlanner.plan(folders.candidate(movie), patterns.folder(), patterns.file()))
                .toList();
        return new Planned(folders.sourceRoot(), withSharedTargetsAsConflicts(plans).stream()
                .sorted((a, b) -> a.currentDirectory().compareToIgnoreCase(b.currentDirectory()))
                .toList());
    }

    /**
     * @throws MovieNotFoundException wenn es den Film nicht gibt
     */
    Planned planMovie(long movieId, MovieRenamePatterns.Parsed patterns) {
        Movie movie = movieRepository.findById(movieId).orElseThrow(() -> new MovieNotFoundException(movieId));
        Planned source = planSource(movie.getSourceId(), patterns);
        return new Planned(source.sourceRoot(), source.plans().stream()
                .filter(plan -> plan.movieId() == movieId)
                .toList());
    }

    private MediaSource movieSource(long sourceId) {
        MediaSource source = sourceService.require(sourceId);
        if (source.getMediaType() != MediaType.MOVIE) {
            throw MovieRenameProblemException.notAMovieSource(source);
        }
        return source;
    }

    /** Zwei Filme dürfen nicht in denselben Ordner wandern. */
    private static List<MovieRenamePlan> withSharedTargetsAsConflicts(List<MovieRenamePlan> plans) {
        Map<String, Long> targets = plans.stream()
                .filter(plan -> plan.status() == Status.RENAME)
                .filter(plan -> !plan.targetDirectory().equals(plan.currentDirectory()))
                .collect(Collectors.groupingBy(plan -> plan.targetDirectory().toLowerCase(Locale.ROOT),
                        Collectors.counting()));
        List<MovieRenamePlan> checked = new ArrayList<>();
        for (MovieRenamePlan plan : plans) {
            boolean shared = plan.status() == Status.RENAME
                    && targets.getOrDefault(plan.targetDirectory().toLowerCase(Locale.ROOT), 0L) > 1;
            checked.add(shared ? new MovieRenamePlan(plan.movieId(), plan.title(), plan.currentDirectory(),
                    plan.targetDirectory(), plan.renames(), Status.CONFLICT,
                    "Mehrere Filme bekämen den Ordner „%s“.".formatted(plan.targetDirectory())) : plan);
        }
        return checked;
    }

    /** Pläne und die Wurzel der Quelle, die beim Aufräumen leerer Ordner nicht unterschritten wird. */
    record Planned(Path sourceRoot, List<MovieRenamePlan> plans) {

        Planned {
            plans = List.copyOf(plans);
        }
    }

    /** Welche Filme sich einen Ordner teilen. */
    private record Folders(Path sourceRoot, Map<Path, List<Movie>> byDirectory,
                           Map<Movie, CompanionFiles> companions) {

        static Folders of(List<Movie> movies, Path sourceRoot) {
            Map<Movie, CompanionFiles> companions = movies.stream()
                    .collect(Collectors.toMap(Function.identity(), Movie::companionFiles));
            Map<Path, List<Movie>> byDirectory = movies.stream()
                    .collect(Collectors.groupingBy(movie -> companions.get(movie).movieDirectory()));
            return new Folders(sourceRoot.toAbsolutePath().normalize(), byDirectory, companions);
        }

        MovieRenamePlanner.Candidate candidate(Movie movie) {
            CompanionFiles files = companions.get(movie);
            Path directory = files.movieDirectory();
            List<Movie> neighbours = byDirectory.get(directory);
            boolean ownsFolder = neighbours.size() == 1 && !directory.equals(sourceRoot)
                    && byDirectory.keySet().stream().noneMatch(other -> !other.equals(directory)
                    && other.startsWith(directory));
            Set<String> otherBaseNames = neighbours.stream()
                    .filter(other -> other != movie)
                    .map(other -> companions.get(other).baseName())
                    .collect(Collectors.toSet());
            MovieNameValues values = MovieNameValues.of(movie);
            return new MovieRenamePlanner.Candidate(movie.getId(), movie.getTitle(), Path.of(movie.getFilePath()),
                    movie.getDiscFormat(), movie.getPartCount(), sourceRoot, ownsFolder, otherBaseNames,
                    values::value);
        }
    }
}
