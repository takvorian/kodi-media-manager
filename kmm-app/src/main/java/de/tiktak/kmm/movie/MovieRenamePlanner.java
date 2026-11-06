package de.tiktak.kmm.movie;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.movie.MovieRenamePlan.FileRename;
import de.tiktak.kmm.movie.MovieRenamePlan.Status;
import de.tiktak.kmm.scanner.CompanionFiles;
import de.tiktak.kmm.scanner.DiscFormat;
import de.tiktak.kmm.scanner.naming.NamePattern;

/**
 * Calculates what a movie would be called according to the patterns, without changing anything. Like Ember:
 * <ul>
 * <li>The folder is only renamed if it belongs to this movie alone. A folder pattern without {@code /} renames it
 * in place, one with {@code /} puts it relative to the source.</li>
 * <li>All files in the folder are renamed whose name starts with the former name of the movie, followed by a dot,
 * hyphen, underscore or space: video, stack parts, NFO, images, subtitles, trailers. If a file belongs to another
 * movie with a longer name in the same folder, it stays untouched.</li>
 * <li>For DVD and Blu-ray folders only the folder is renamed.</li>
 * <li>If a target is taken already, nothing is changed (conflict).</li>
 * </ul>
 */
final class MovieRenamePlanner {

    private static final String NAME_SEPARATORS = ".-_ ";

    private MovieRenamePlanner() {
    }

    static MovieRenamePlan plan(Candidate candidate, NamePattern folderPattern, NamePattern filePattern) {
        CompanionFiles companions = CompanionFiles.of(candidate.mainFile(), candidate.discFormat(),
                candidate.partCount());
        Path currentDirectory = companions.movieDirectory();
        Planned planned = new Planned(candidate, currentDirectory);
        Path targetDirectory = currentDirectory;
        if (candidate.ownsFolder()) {
            List<String> segments = folderPattern.render(candidate.values());
            if (segments.isEmpty()) {
                return planned.failed(LocalizedText.of("movie.rename.folderPatternEmpty"));
            }
            Path base = folderPattern.hasFolders() ? candidate.sourceRoot() : currentDirectory.getParent();
            targetDirectory = resolve(base, segments);
        }
        List<FileRename> renames = List.of();
        if (candidate.discFormat() == DiscFormat.FILE) {
            List<String> name = filePattern.render(candidate.values());
            if (name.size() != 1) {
                return planned.failed(LocalizedText.of("movie.rename.filePatternInvalid"));
            }
            try {
                renames = fileRenames(currentDirectory, companions.baseName(), name.getFirst(),
                        candidate.otherBaseNames());
            } catch (IOException e) {
                return planned.failed(LocalizedText.of("movie.rename.folderUnreadable")
                        .with("reason", LocalizedReasons.of(e)));
            }
        }
        return planned.result(targetDirectory, renames);
    }

    private static Path resolve(Path base, List<String> segments) {
        Path target = base;
        for (String segment : segments) {
            target = target.resolve(segment);
        }
        return target;
    }

    private static List<FileRename> fileRenames(Path directory, String currentBase, String targetBase,
                                                Set<String> otherBaseNames) throws IOException {
        List<FileRename> renames = new ArrayList<>();
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                String name = file.getFileName().toString();
                if (belongsTo(name, currentBase) && otherBaseNames.stream()
                        .noneMatch(other -> other.length() > currentBase.length() && belongsTo(name, other))) {
                    String target = targetBase + name.substring(currentBase.length());
                    if (!target.equals(name)) {
                        renames.add(new FileRename(name, target));
                    }
                }
            }
        }
        return renames;
    }

    private static boolean belongsTo(String fileName, String baseName) {
        return fileName.length() > baseName.length()
                && fileName.regionMatches(true, 0, baseName, 0, baseName.length())
                && NAME_SEPARATORS.indexOf(fileName.charAt(baseName.length())) >= 0;
    }

    /**
     * A movie as the planner needs it.
     *
     * @param mainFile       main file (the first part for stacks, the marker file for discs)
     * @param sourceRoot     root of the source
     * @param ownsFolder     whether the folder belongs to this movie alone and is not the source itself
     * @param otherBaseNames names of other movies in the same folder
     * @param values         values of the placeholders
     */
    record Candidate(long movieId, String title, Path mainFile, DiscFormat discFormat, int partCount,
                     Path sourceRoot, boolean ownsFolder, Set<String> otherBaseNames,
                     Function<String, String> values) {
    }

    private record Planned(Candidate candidate, Path currentDirectory) {

        MovieRenamePlan failed(LocalizedText message) {
            return plan(currentDirectory, List.of(), Status.FAILED, message);
        }

        MovieRenamePlan result(Path targetDirectory, List<FileRename> renames) {
            boolean sameDirectory = targetDirectory.toString().equals(currentDirectory.toString());
            if (sameDirectory && renames.isEmpty()) {
                return plan(targetDirectory, renames, Status.UNCHANGED, null);
            }
            LocalizedText conflict = sameDirectory ? null : directoryConflict(targetDirectory);
            if (conflict == null) {
                conflict = fileConflict(renames);
            }
            return conflict == null ? plan(targetDirectory, renames, Status.RENAME, null)
                    : plan(targetDirectory, renames, Status.CONFLICT, conflict);
        }

        /** Changing upper and lower case only is no conflict, even though Windows finds the folder. */
        private LocalizedText directoryConflict(Path targetDirectory) {
            boolean sameIgnoringCase = targetDirectory.toString().toLowerCase(Locale.ROOT)
                    .equals(currentDirectory.toString().toLowerCase(Locale.ROOT));
            if (!sameIgnoringCase && Files.exists(targetDirectory)) {
                return LocalizedText.of("movie.rename.folderExists").with("folder", targetDirectory);
            }
            if (targetDirectory.startsWith(currentDirectory) && !sameIgnoringCase) {
                return LocalizedText.of("movie.rename.folderInsideCurrent");
            }
            return null;
        }

        private LocalizedText fileConflict(List<FileRename> renames) {
            Set<String> renamed = Set.copyOf(renames.stream()
                    .map(rename -> rename.currentName().toLowerCase(Locale.ROOT)).toList());
            for (FileRename rename : renames) {
                String target = rename.targetName();
                boolean freed = renamed.contains(target.toLowerCase(Locale.ROOT));
                if (!freed && Files.exists(currentDirectory.resolve(target))) {
                    return LocalizedText.of("movie.rename.fileExists").with("file", target);
                }
            }
            return null;
        }

        private MovieRenamePlan plan(Path targetDirectory, List<FileRename> renames, Status status,
                                     LocalizedText message) {
            return new MovieRenamePlan(candidate.movieId(), candidate.title(), currentDirectory.toString(),
                    targetDirectory.toString(), renames, status, message);
        }
    }
}
