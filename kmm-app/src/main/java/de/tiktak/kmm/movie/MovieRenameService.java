package de.tiktak.kmm.movie;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tiktak.kmm.movie.MovieRenamePlan.Status;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.scanner.CompanionFiles;
import de.tiktak.kmm.source.MediaSource;
import de.tiktak.kmm.source.MediaSourceService;
import lombok.RequiredArgsConstructor;

/**
 * Benennt Filme nach Mustern um. Die Vorschau ändert nichts.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MovieRenameService {

    private final MediaSourceService sourceService;
    private final MovieRepository movieRepository;

    /**
     * @return je Film der Quelle, was das Umbenennen ändern würde, sortiert nach Ordner
     * @throws MovieRenameProblemException wenn die Quelle keine Filme enthält oder ein Muster ungültig ist
     */
    public List<MovieRenamePlan> preview(long sourceId, MovieRenamePatterns patterns) {
        MediaSource source = sourceService.require(sourceId);
        if (source.getMediaType() != MediaType.MOVIE) {
            throw MovieRenameProblemException.notAMovieSource(source);
        }
        MovieRenamePatterns.Parsed parsed = patterns.parse();
        List<Movie> movies = movieRepository.findBySourceId(sourceId);
        Folders folders = Folders.of(movies, Path.of(source.getPath()));
        List<MovieRenamePlan> plans = movies.stream()
                .map(movie -> MovieRenamePlanner.plan(folders.candidate(movie), parsed.folder(), parsed.file()))
                .toList();
        return withSharedTargetsAsConflicts(plans).stream()
                .sorted((a, b) -> a.currentDirectory().compareToIgnoreCase(b.currentDirectory()))
                .toList();
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
