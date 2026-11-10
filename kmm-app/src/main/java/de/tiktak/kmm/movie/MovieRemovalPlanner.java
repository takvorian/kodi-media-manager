package de.tiktak.kmm.movie;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.movie.MovieRemoval.RemovedMovie;
import de.tiktak.kmm.removal.RemovalFiles;
import de.tiktak.kmm.scanner.CompanionFiles;
import de.tiktak.kmm.scanner.DiscFormat;
import de.tiktak.kmm.scanner.StackedFiles;
import de.tiktak.kmm.scanner.removal.FileRemoval;
import de.tiktak.kmm.source.MediaSource;
import de.tiktak.kmm.source.MediaSourceService;
import lombok.RequiredArgsConstructor;

/**
 * Works out what removing movies comprises, without changing anything:
 * <ul>
 * <li>the video file, or all parts of a multi-part movie, with the companion files named after it (NFO, images,
 * subtitles, trailer …); files of other movies in the same folder stay untouched,</li>
 * <li>for a movie in a folder of its own also {@code movie.nfo}; the folder goes if nothing else is left in it,</li>
 * <li>for DVD and Blu-ray folders the whole movie folder.</li>
 * </ul>
 * Empty folders are cleaned up up to the source.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
class MovieRemovalPlanner {

    private final MovieRepository movieRepository;
    private final MediaSourceService sourceService;

    /**
     * @throws MovieNotFoundException if a movie does not exist
     */
    MovieRemoval movies(List<Long> movieIds) {
        List<Movie> movies = new LinkedHashSet<>(movieIds).stream()
                .map(id -> movieRepository.findById(id).orElseThrow(() -> new MovieNotFoundException(id)))
                .toList();
        RemovalFiles files = new RemovalFiles();
        Map<Long, Source> sources = new HashMap<>();
        movies.forEach(movie -> add(files, movie, sources.computeIfAbsent(movie.getSourceId(), this::source)));
        movies.stream().filter(movie -> sources.get(movie.getSourceId()).ownsFolder(movie))
                .forEach(movie -> noteKeptFolder(files, movie.companionFiles().movieDirectory()));
        return new MovieRemoval(
                movies.stream().map(movie -> new RemovedMovie(movie.getId(), movie.getTitle(), movie.getFilePath()))
                        .toList(),
                files,
                movies.stream().map(MovieRemovalPlanner::entry).toList());
    }

    private Source source(long sourceId) {
        MediaSource source = sourceService.require(sourceId);
        return Source.of(source, movieRepository.findBySourceId(sourceId));
    }

    private static void add(RemovalFiles files, Movie movie, Source source) {
        CompanionFiles companions = movie.companionFiles();
        Path directory = companions.movieDirectory();
        RemovalFiles.Scope scope = files.scope(source.root(), source.name(), source.root());
        if (movie.getDiscFormat() != DiscFormat.FILE) {
            if (source.sharesFolder(movie)) {
                files.block(LocalizedText.of("removal.note.sharedDiscFolder").with("folder", directory));
            } else {
                scope.addDirectory(directory);
            }
            return;
        }
        Path mainFile = Path.of(movie.getFilePath());
        try {
            scope.addFiles(movie.getPartCount() > 1 ? StackedFiles.parts(mainFile) : List.of(mainFile));
        } catch (UncheckedIOException e) {
            files.unreadable(directory, e.getCause());
            return;
        }
        scope.addCompanions(directory, companions.baseName(), source.otherBaseNames(movie));
        if (source.ownsFolder(movie)) {
            companions.nfoCandidates().stream()
                    .filter(nfo -> Files.exists(nfo, LinkOption.NOFOLLOW_LINKS))
                    .forEach(scope::addFile);
        }
    }

    /** Tells which files keep a folder of its own alive. */
    private static void noteKeptFolder(RemovalFiles files, Path directory) {
        try {
            List<Path> kept = FileRemoval.listFiles(directory).stream()
                    .filter(file -> !files.contains(file))
                    .toList();
            if (!kept.isEmpty()) {
                files.note(LocalizedText.of("removal.note.folderKept").with("folder", directory)
                        .with("count", kept.size()));
            }
        } catch (IOException e) {
            // the folder is gone or unreadable; adding its files has noted that already
        }
    }

    private static LocalizedText entry(Movie movie) {
        String year = movie.getReleaseYear() == null ? "" : " (%d)".formatted(movie.getReleaseYear());
        return LocalizedText.literal(movie.getTitle() + year);
    }

    /** A source with its movies by folder. */
    private record Source(Path root, String name, Map<Path, List<Movie>> byDirectory) {

        static Source of(MediaSource source, List<Movie> movies) {
            return new Source(Path.of(source.getPath()).toAbsolutePath().normalize(), source.getName(),
                    movies.stream().collect(Collectors.groupingBy(movie -> movie.companionFiles().movieDirectory())));
        }

        /** Whether other movies lie in the folder of the movie or below it. */
        boolean sharesFolder(Movie movie) {
            Path directory = movie.companionFiles().movieDirectory();
            return byDirectory.getOrDefault(directory, List.of()).size() > 1 || byDirectory.keySet().stream()
                    .anyMatch(other -> !other.equals(directory) && other.startsWith(directory));
        }

        /** Whether the folder belongs to the movie alone and is not the source itself. */
        boolean ownsFolder(Movie movie) {
            return !sharesFolder(movie) && !movie.companionFiles().movieDirectory().equals(root);
        }

        Set<String> otherBaseNames(Movie movie) {
            return byDirectory.getOrDefault(movie.companionFiles().movieDirectory(), List.of()).stream()
                    .filter(other -> !other.getId().equals(movie.getId()))
                    .map(other -> other.companionFiles().baseName())
                    .collect(Collectors.toSet());
        }
    }
}
