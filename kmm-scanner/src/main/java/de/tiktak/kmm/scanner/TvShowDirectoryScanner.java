package de.tiktak.kmm.scanner;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Durchsucht eine Serienquelle wie Kodi: Jeder Ordner direkt unterhalb der Quelle ist eine Serie,
 * ihre Episoden liegen beliebig tief darin (z. B. in {@code Staffel 1}). Staffel und Episode stehen
 * im Dateinamen; Staffelordner werden nicht ausgewertet.
 */
@Slf4j
@RequiredArgsConstructor
public class TvShowDirectoryScanner {

    private static final Pattern NON_EPISODE_FILE = Pattern.compile("(?i)(?:^|[ ._-])(?:sample|trailer)$");
    private static final char EXTENSION_SEPARATOR = '.';

    private final ScanRules rules;
    private final MovieNameParser nameParser;
    private final EpisodeNameParser episodeParser;

    /**
     * @throws UncheckedIOException wenn das Quellverzeichnis nicht gelesen werden kann
     */
    public List<TvShowCandidate> scan(Path root) {
        return showDirectories(root).stream()
                .map(this::scanShow)
                .flatMap(Optional::stream)
                .toList();
    }

    /**
     * @return die möglichen Serienordner der Quelle, sortiert; ob sie Episoden enthalten, prüft erst
     *         {@link #scanShow(Path)}
     * @throws UncheckedIOException wenn das Quellverzeichnis nicht gelesen werden kann
     */
    public List<Path> showDirectories(Path root) {
        Objects.requireNonNull(root, "root must not be null");
        try (Stream<Path> entries = Files.list(root)) {
            return entries
                    .filter(Files::isDirectory)
                    .filter(directory -> !rules.isIgnoredDirectory(directory.getFileName().toString()))
                    .filter(directory -> !rules.isExcludedDirectory(directory))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Durchsucht einen Serienordner. Ordner ohne Videodateien sind keine Serie.
     *
     * @return die Serie oder leer, wenn der Ordner keine Videodateien enthält oder unlesbar ist
     */
    public Optional<TvShowCandidate> scanShow(Path directory) {
        EpisodeVisitor visitor = new EpisodeVisitor(directory);
        try {
            Files.walkFileTree(directory, visitor);
        } catch (IOException e) {
            log.warn("Skipping unreadable TV show directory {}: {}", directory, e.getMessage());
            return Optional.empty();
        }
        if (visitor.episodes.isEmpty() && visitor.unrecognized.isEmpty()) {
            return Optional.empty();
        }
        ParsedMovieName name = nameParser.parse(directory.getFileName().toString());
        String title = name.hasTitle() ? name.title() : directory.getFileName().toString();
        visitor.episodes.sort(Comparator.comparingInt((EpisodeCandidate episode) -> episode.number().season())
                .thenComparingInt(episode -> episode.number().firstEpisode())
                .thenComparing(episode -> episode.file().toString()));
        visitor.unrecognized.sort(Comparator.naturalOrder());
        return Optional.of(new TvShowCandidate(directory, title, name.year(), visitor.episodes, visitor.unrecognized));
    }

    private final class EpisodeVisitor extends SimpleFileVisitor<Path> {

        private final Path showDirectory;
        private final List<EpisodeCandidate> episodes = new ArrayList<>();
        private final List<Path> unrecognized = new ArrayList<>();

        private EpisodeVisitor(Path showDirectory) {
            this.showDirectory = showDirectory;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
            if (directory.equals(showDirectory)) {
                return FileVisitResult.CONTINUE;
            }
            boolean skip = rules.isIgnoredDirectory(directory.getFileName().toString())
                    || rules.isExcludedDirectory(directory);
            return skip ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
            String fileName = file.getFileName().toString();
            int extensionStart = fileName.lastIndexOf(EXTENSION_SEPARATOR);
            if (!attributes.isRegularFile() || extensionStart <= 0
                    || !rules.isVideoExtension(fileName.substring(extensionStart + 1))) {
                return FileVisitResult.CONTINUE;
            }
            String name = fileName.substring(0, extensionStart);
            if (NON_EPISODE_FILE.matcher(name).find()) {
                return FileVisitResult.CONTINUE;
            }
            episodeParser.parse(name).ifPresentOrElse(
                    number -> episodes.add(new EpisodeCandidate(file, number,
                            episodeParser.titleAfterNumber(name).orElse(null))),
                    () -> unrecognized.add(file));
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exception) {
            log.warn("Skipping unreadable path {}: {}", file, exception.getMessage());
            return FileVisitResult.CONTINUE;
        }
    }
}
