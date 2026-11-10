package de.tiktak.kmm.scanner.removal;

import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.scanner.naming.CompanionNames;
import de.tiktak.kmm.scanner.naming.FileMoves;
import lombok.extern.slf4j.Slf4j;

/**
 * Finds and deletes the files of movies, TV shows and episodes for good, without a recycle bin. Symbolic links are
 * never followed: a link is listed and deleted as a file of its own, its target stays untouched.
 */
@Slf4j
public final class FileRemoval {

    private FileRemoval() {
    }

    /**
     * @return all files below the folder, also in subfolders, sorted; folders themselves are not listed
     * @throws IOException if a folder cannot be read
     */
    public static List<Path> listFiles(Path directory) throws IOException {
        List<Path> files = new ArrayList<>();
        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                files.add(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException e) throws IOException {
                throw e;
            }
        });
        return files.stream().sorted().toList();
    }

    /**
     * Files in a folder that belong to a base name according to {@link CompanionNames}: video files, stack parts and
     * their companion files.
     *
     * @param baseName       common start of the names, e.g. {@code Heat} for {@code Heat.mkv} or {@code Heat-cd1.avi}
     * @param otherBaseNames base names of other videos in the same folder whose files are left alone
     * @throws IOException if the folder cannot be read
     */
    public static List<Path> companions(Path directory, String baseName, Collection<String> otherBaseNames)
            throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(file -> !Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS))
                    .filter(file -> CompanionNames.belongsOnlyTo(file.getFileName().toString(), baseName,
                            otherBaseNames))
                    .sorted()
                    .toList();
        }
    }

    /** @return whether the path lies below the root; the root itself does not */
    public static boolean isInside(Path root, Path path) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedPath = path.toAbsolutePath().normalize();
        return normalizedPath.startsWith(normalizedRoot) && !normalizedPath.equals(normalizedRoot);
    }

    /**
     * Folders that are empty once the files are deleted: the folders of the files and the ones above them up to
     * {@code stop} exclusively, as far as they contain nothing but these files and empty folders.
     *
     * @return the folders, sorted
     * @throws IOException if a folder cannot be read
     */
    public static List<Path> emptiedDirectories(Collection<Path> files, Path stop) throws IOException {
        Set<Path> deleted = new HashSet<>(files);
        Map<Path, Boolean> emptied = new HashMap<>();
        Set<Path> result = new TreeSet<>();
        for (Path file : files) {
            for (Path directory = file.getParent(); directory != null && isInside(stop, directory);
                    directory = directory.getParent()) {
                Boolean empty = emptied.get(directory);
                if (empty == null) {
                    empty = deleted.containsAll(listFiles(directory));
                    emptied.put(directory, empty);
                }
                if (!empty) {
                    break;
                }
                result.add(directory);
            }
        }
        return List.copyOf(result);
    }

    /**
     * Deletes the files, then the folders as far as they are empty afterwards (with empty subfolders), then the
     * empty folders above up to {@code stop} exclusively. A file that cannot be deleted does not stop the others.
     *
     * @param directories folders that should be empty once the files are gone
     * @return the deleted files and one message per file that could not be deleted
     */
    public static Deletion delete(Collection<Path> files, Collection<Path> directories, Path stop) {
        List<Path> deleted = new ArrayList<>();
        List<LocalizedText> failures = new ArrayList<>();
        for (Path file : files) {
            try {
                if (Files.deleteIfExists(file)) {
                    deleted.add(file);
                }
            } catch (IOException e) {
                log.warn("File {} not deleted: {}", file, e.toString());
                failures.add(LocalizedText.of("removal.deleteFailed").with("file", file).with("reason", reason(e)));
            }
        }
        directories.stream()
                .sorted(Comparator.comparingInt(Path::getNameCount).reversed())
                .forEach(FileRemoval::deleteEmptyTree);
        files.stream().map(Path::getParent).distinct()
                .forEach(directory -> FileMoves.removeEmptyDirectories(directory, stop));
        directories.stream().map(Path::getParent).distinct()
                .forEach(directory -> FileMoves.removeEmptyDirectories(directory, stop));
        return new Deletion(deleted, failures);
    }

    /** Deletes the folder with its subfolders if there are no files left in it. */
    private static void deleteEmptyTree(Path directory) {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try {
            Files.walkFileTree(directory, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                    throw new DirectoryNotEmptyException(file.getParent().toString());
                }

                @Override
                public FileVisitResult postVisitDirectory(Path folder, IOException e) throws IOException {
                    if (e != null) {
                        throw e;
                    }
                    Files.delete(folder);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (DirectoryNotEmptyException | NoSuchFileException e) {
            log.debug("Folder {} kept: {}", directory, e.getMessage());
        } catch (IOException e) {
            log.warn("Folder {} not removed: {}", directory, e.toString());
        }
    }

    private static String reason(IOException e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}
