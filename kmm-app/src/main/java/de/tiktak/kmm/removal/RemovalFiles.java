package de.tiktak.kmm.removal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.scanner.naming.CompanionNames;
import de.tiktak.kmm.scanner.removal.Deletion;
import de.tiktak.kmm.scanner.removal.FileRemoval;

/**
 * Collects the files of a removal and deletes them. Only files below the root of their source are accepted, never the
 * source itself; otherwise, or if a folder cannot be read, no file may be deleted and a note says why. Empty folders
 * are cleaned up afterwards, up to a stop folder per scope (e.g. the TV show folder for episodes).
 */
public final class RemovalFiles {

    /** At most this many file names are listed in a note. */
    static final int MAX_LISTED_FILES = 10;
    private static final String MORE = "…";

    private final Map<Scope, Set<Path>> files = new LinkedHashMap<>();
    private final Set<LocalizedText> notes = new LinkedHashSet<>();
    private final Set<Path> missing = new TreeSet<>();
    private Map<Scope, List<Path>> emptiedDirectories;
    private boolean blocked;

    /**
     * @param sourceRoot root of the source; nothing outside it and not the root itself is deleted
     * @param sourceName name of the source for notes
     * @param stop       empty folders are removed up to this folder exclusively
     */
    public Scope scope(Path sourceRoot, String sourceName, Path stop) {
        Scope scope = new Scope(sourceRoot.toAbsolutePath().normalize(), sourceName, stop.toAbsolutePath().normalize());
        files.computeIfAbsent(scope, key -> new TreeSet<>());
        return scope;
    }

    public void note(LocalizedText note) {
        notes.add(note);
    }

    /** Whether a file is to be deleted. */
    public boolean contains(Path file) {
        Path normalized = file.toAbsolutePath().normalize();
        return files.values().stream().anyMatch(scopeFiles -> scopeFiles.contains(normalized));
    }

    /** Whether the files may be deleted; if not, the notes say why. Reads the folders on the first call. */
    public boolean canDelete() {
        emptiedDirectories();
        return !blocked;
    }

    /** A folder needed for the removal cannot be read: no file may be deleted. */
    public void unreadable(Path directory, IOException e) {
        block(LocalizedText.of("removal.note.unreadable").with("folder", directory)
                .with("reason", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
    }

    /** No file may be deleted, for the given reason. */
    public void block(LocalizedText reason) {
        blocked = true;
        notes.add(reason);
    }

    /** The preview of the removal with the given entries. */
    public RemovalPlan plan(List<LocalizedText> entries) {
        Map<Scope, List<Path>> directories = emptiedDirectories();
        List<LocalizedText> allNotes = new ArrayList<>(notes);
        if (!missing.isEmpty()) {
            allNotes.add(LocalizedText.of("removal.note.missingFiles").with("count", missing.size())
                    .with("files", listing(missing)));
        }
        return new RemovalPlan(entries, sortedStrings(files.values()), sortedStrings(directories.values()), allNotes,
                !blocked);
    }

    /**
     * Deletes the files for good and cleans up the folders emptied by it.
     *
     * @throws RemovalProblemException if files lie outside the source or folders cannot be read
     */
    public Deletion delete() {
        Map<Scope, List<Path>> directories = emptiedDirectories();
        if (blocked) {
            throw RemovalProblemException.filesBlocked();
        }
        List<Path> deleted = new ArrayList<>();
        List<LocalizedText> failures = new ArrayList<>();
        files.forEach((scope, scopeFiles) -> {
            Deletion deletion = FileRemoval.delete(scopeFiles, directories.get(scope), scope.stop());
            deleted.addAll(deletion.deleted());
            failures.addAll(deletion.failures());
        });
        return new Deletion(deleted, failures);
    }

    /** Lists file names for a note: at most {@value #MAX_LISTED_FILES}, then an ellipsis. */
    public static String listing(Collection<Path> paths) {
        List<String> names = paths.stream().map(path -> path.getFileName().toString()).toList();
        String listed = String.join(", ", names.subList(0, Math.min(names.size(), MAX_LISTED_FILES)));
        return names.size() > MAX_LISTED_FILES ? listed + ", " + MORE : listed;
    }

    private Map<Scope, List<Path>> emptiedDirectories() {
        if (emptiedDirectories == null) {
            emptiedDirectories = new LinkedHashMap<>();
            files.forEach((scope, scopeFiles) -> emptiedDirectories.put(scope, emptiedDirectories(scope, scopeFiles)));
        }
        return emptiedDirectories;
    }

    private List<Path> emptiedDirectories(Scope scope, Set<Path> scopeFiles) {
        try {
            return FileRemoval.emptiedDirectories(scopeFiles, scope.stop());
        } catch (IOException e) {
            unreadable(scope.stop(), e);
            return List.of();
        }
    }

    private static List<String> sortedStrings(Collection<? extends Collection<Path>> paths) {
        return paths.stream().flatMap(Collection::stream).map(Path::toString).sorted().distinct()
                .collect(Collectors.toList());
    }

    /** The files of one source with one stop folder for cleaning up. */
    public final class Scope {

        private final Path root;
        private final String sourceName;
        private final Path stop;

        private Scope(Path root, String sourceName, Path stop) {
            this.root = root;
            this.sourceName = sourceName;
            this.stop = stop;
        }

        Path stop() {
            return stop;
        }

        /** Adds a file; a file that does not exist is listed as missing in a note. */
        public void addFile(Path file) {
            Path normalized = file.toAbsolutePath().normalize();
            if (!inside(normalized)) {
                return;
            }
            if (Files.exists(normalized, LinkOption.NOFOLLOW_LINKS)) {
                files.get(this).add(normalized);
            } else {
                missing.add(normalized);
            }
        }

        public void addFiles(Collection<Path> paths) {
            paths.forEach(this::addFile);
        }

        /**
         * Adds a video file with its companion files.
         *
         * @param otherBaseNames base names of other videos in the same folder whose files are left alone
         */
        public void addWithCompanions(Path videoFile, Collection<String> otherBaseNames) {
            Path normalized = videoFile.toAbsolutePath().normalize();
            if (inside(normalized)) {
                addFile(normalized);
                addCompanions(normalized.getParent(), CompanionNames.baseName(normalized), otherBaseNames);
            }
        }

        /**
         * Adds the files of a folder that belong to a base name, e.g. all parts of a multi-part movie with their
         * companion files; a folder that does not exist adds nothing.
         *
         * @param otherBaseNames base names of other videos in the same folder whose files are left alone
         */
        public void addCompanions(Path directory, String baseName, Collection<String> otherBaseNames) {
            Path normalized = directory.toAbsolutePath().normalize();
            if (!normalized.equals(root) && !inside(normalized)) {
                return;
            }
            try {
                addFiles(FileRemoval.companions(normalized, baseName, otherBaseNames));
            } catch (NoSuchFileException e) {
                // the folder is gone; missing video files are noted already
            } catch (IOException e) {
                unreadable(normalized, e);
            }
        }

        /**
         * Adds a folder with everything in it.
         *
         * @return the files in the folder, empty if it cannot be added
         */
        public List<Path> addDirectory(Path directory) {
            Path normalized = directory.toAbsolutePath().normalize();
            if (normalized.equals(root)) {
                block(LocalizedText.of("removal.note.sourceItself").with("folder", normalized));
                return List.of();
            }
            if (!inside(normalized)) {
                return List.of();
            }
            try {
                List<Path> content = FileRemoval.listFiles(normalized);
                addFiles(content);
                return content;
            } catch (NoSuchFileException e) {
                missing.add(normalized);
                return List.of();
            } catch (IOException e) {
                unreadable(normalized, e);
                return List.of();
            }
        }

        private boolean inside(Path path) {
            if (FileRemoval.isInside(root, path)) {
                return true;
            }
            block(LocalizedText.of("removal.note.outsideSource").with("path", path).with("source", sourceName));
            return false;
        }
    }
}
