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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Durchsucht ein Quellverzeichnis rekursiv nach Filmen. Erkennt Einzeldateien, mehrteilige Filme
 * ({@code cd1}/{@code cd2}), DVD- und Blu-ray-Ordner und überspringt Extras, Trailer und Samples.
 */
@Slf4j
@RequiredArgsConstructor
public class MovieDirectoryScanner {

    static final String DVD_DIRECTORY = "VIDEO_TS";
    static final String DVD_MARKER_FILE = "VIDEO_TS.IFO";
    static final String BLURAY_DIRECTORY = "BDMV";
    static final String BLURAY_MARKER_FILE = "index.bdmv";

    private static final Pattern NON_MOVIE_FILE = Pattern.compile("(?i)(?:^|[ ._-])(?:sample|trailer)$");
    private static final char EXTENSION_SEPARATOR = '.';

    private final ScanRules rules;
    private final MovieNameParser nameParser;

    /**
     * @throws UncheckedIOException wenn das Verzeichnis nicht gelesen werden kann
     */
    public List<MovieCandidate> scan(Path root) {
        Objects.requireNonNull(root, "root must not be null");
        CollectingVisitor visitor = new CollectingVisitor(root);
        try {
            Files.walkFileTree(root, visitor);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return visitor.candidates();
    }

    private final class CollectingVisitor extends SimpleFileVisitor<Path> {

        private final Path root;
        private final List<MovieCandidate> discs = new ArrayList<>();
        private final List<MovieCandidate> singleFiles = new ArrayList<>();
        private final Map<String, List<StackPart>> stacks = new LinkedHashMap<>();

        private CollectingVisitor(Path root) {
            this.root = root;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
            if (directory.equals(root)) {
                return FileVisitResult.CONTINUE;
            }
            String name = directory.getFileName().toString();
            if (rules.isIgnoredDirectory(name) || rules.isExcludedDirectory(directory)) {
                return FileVisitResult.SKIP_SUBTREE;
            }
            Optional<MovieCandidate> disc = discIn(directory, name);
            disc.ifPresent(discs::add);
            return disc.isPresent() ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
            String fileName = file.getFileName().toString();
            int extensionStart = fileName.lastIndexOf(EXTENSION_SEPARATOR);
            if (!attributes.isRegularFile() || extensionStart <= 0) {
                return FileVisitResult.CONTINUE;
            }
            String nameWithoutExtension = fileName.substring(0, extensionStart);
            String extension = fileName.substring(extensionStart + 1);
            if (rules.isVideoExtension(extension) && !NON_MOVIE_FILE.matcher(nameWithoutExtension).find()) {
                collectVideoFile(file, nameWithoutExtension, extension);
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exception) {
            if (file.equals(root)) {
                throw new UncheckedIOException(exception);
            }
            log.warn("Skipping unreadable path {}: {}", file, exception.getMessage());
            return FileVisitResult.CONTINUE;
        }

        private void collectVideoFile(Path file, String nameWithoutExtension, String extension) {
            Optional<StackedFile> stacked = StackedFile.of(file, nameWithoutExtension, extension);
            if (stacked.isPresent()) {
                stacks.computeIfAbsent(stacked.get().stackKey(), key -> new ArrayList<>())
                        .add(new StackPart(file, stacked.get()));
            } else {
                singleFiles.add(candidateFor(file, nameWithoutExtension, DiscFormat.FILE, 1));
            }
        }

        private Optional<MovieCandidate> discIn(Path directory, String name) {
            if (name.equalsIgnoreCase(DVD_DIRECTORY)) {
                return discCandidate(directory, DVD_MARKER_FILE, DiscFormat.DVD);
            }
            if (name.equalsIgnoreCase(BLURAY_DIRECTORY)) {
                return discCandidate(directory, BLURAY_MARKER_FILE, DiscFormat.BLURAY);
            }
            return Optional.empty();
        }

        private Optional<MovieCandidate> discCandidate(Path directory, String markerFile, DiscFormat format) {
            String movieDirectoryName = directory.getParent().getFileName().toString();
            return findIgnoringCase(directory, markerFile)
                    .map(marker -> candidateFor(marker, movieDirectoryName, format, 1));
        }

        private List<MovieCandidate> candidates() {
            List<MovieCandidate> candidates = new ArrayList<>(discs);
            candidates.addAll(singleFiles);
            stacks.values().forEach(parts -> candidates.add(stackCandidate(parts)));
            candidates.sort(Comparator.comparing(candidate -> candidate.file().toString()));
            return List.copyOf(candidates);
        }

        private MovieCandidate stackCandidate(List<StackPart> parts) {
            StackPart first = parts.stream().min(Comparator.comparingInt(part -> part.stacked().partNumber())).orElseThrow();
            return candidateFor(first.file(), first.stacked().baseName(), DiscFormat.FILE, parts.size());
        }

        private MovieCandidate candidateFor(Path file, String name, DiscFormat format, int partCount) {
            ParsedMovieName parsed = nameParser.parse(name);
            if (!parsed.hasTitle() && file.getParent() != null && !file.getParent().equals(root)) {
                parsed = nameParser.parse(file.getParent().getFileName().toString());
            }
            String title = parsed.hasTitle() ? parsed.title() : name;
            return new MovieCandidate(file, title, parsed.year(), format, partCount);
        }
    }

    private static Optional<Path> findIgnoringCase(Path directory, String fileName) {
        Path exact = directory.resolve(fileName);
        if (Files.isRegularFile(exact)) {
            return Optional.of(exact);
        }
        try (var files = Files.list(directory)) {
            return files.filter(file -> file.getFileName().toString().toLowerCase(Locale.ROOT)
                            .equals(fileName.toLowerCase(Locale.ROOT)))
                    .filter(Files::isRegularFile)
                    .findFirst();
        } catch (IOException e) {
            log.warn("Skipping unreadable directory {}: {}", directory, e.getMessage());
            return Optional.empty();
        }
    }

    private record StackPart(Path file, StackedFile stacked) {
    }
}
