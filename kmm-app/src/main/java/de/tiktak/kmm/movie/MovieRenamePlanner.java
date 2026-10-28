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

import de.tiktak.kmm.movie.MovieRenamePlan.FileRename;
import de.tiktak.kmm.movie.MovieRenamePlan.Status;
import de.tiktak.kmm.scanner.CompanionFiles;
import de.tiktak.kmm.scanner.DiscFormat;
import de.tiktak.kmm.scanner.naming.NamePattern;

/**
 * Berechnet, wie ein Film nach den Mustern heißen würde, ohne etwas zu ändern. Wie Ember:
 * <ul>
 * <li>Der Ordner wird nur umbenannt, wenn er allein diesem Film gehört. Ein Ordnermuster ohne {@code /}
 * benennt ihn an Ort und Stelle um, eines mit {@code /} legt ihn relativ zur Quelle ab.</li>
 * <li>Umbenannt werden alle Dateien im Ordner, deren Name mit dem bisherigen Namen des Films beginnt,
 * gefolgt von Punkt, Bindestrich, Unterstrich oder Leerzeichen: Video, Stapelteile, NFO, Bilder,
 * Untertitel, Trailer. Gehört eine Datei zu einem anderen Film mit längerem Namen im selben Ordner, bleibt
 * sie unberührt.</li>
 * <li>Bei DVD- und Blu-ray-Ordnern wird nur der Ordner umbenannt.</li>
 * <li>Ist ein Ziel schon belegt, wird nichts geändert (Konflikt).</li>
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
                return planned.failed("Das Ordnermuster ergibt keinen Namen.");
            }
            Path base = folderPattern.hasFolders() ? candidate.sourceRoot() : currentDirectory.getParent();
            targetDirectory = resolve(base, segments);
        }
        List<FileRename> renames = List.of();
        if (candidate.discFormat() == DiscFormat.FILE) {
            List<String> name = filePattern.render(candidate.values());
            if (name.size() != 1) {
                return planned.failed("Das Dateimuster ergibt keinen gültigen Dateinamen.");
            }
            try {
                renames = fileRenames(currentDirectory, companions.baseName(), name.getFirst(),
                        candidate.otherBaseNames());
            } catch (IOException e) {
                return planned.failed("Der Ordner ist nicht lesbar: " + e.getMessage());
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
     * Ein Film, wie ihn der Planer braucht.
     *
     * @param mainFile       Hauptdatei (bei Stapeln der erste Teil, bei Discs die Markerdatei)
     * @param sourceRoot     Wurzel der Quelle
     * @param ownsFolder     ob der Ordner allein diesem Film gehört und nicht die Quelle selbst ist
     * @param otherBaseNames Namen anderer Filme im selben Ordner
     * @param values         Werte der Platzhalter
     */
    record Candidate(long movieId, String title, Path mainFile, DiscFormat discFormat, int partCount,
                     Path sourceRoot, boolean ownsFolder, Set<String> otherBaseNames,
                     Function<String, String> values) {
    }

    private record Planned(Candidate candidate, Path currentDirectory) {

        MovieRenamePlan failed(String message) {
            return plan(currentDirectory, List.of(), Status.FAILED, message);
        }

        MovieRenamePlan result(Path targetDirectory, List<FileRename> renames) {
            boolean sameDirectory = targetDirectory.toString().equals(currentDirectory.toString());
            if (sameDirectory && renames.isEmpty()) {
                return plan(targetDirectory, renames, Status.UNCHANGED, null);
            }
            String conflict = sameDirectory ? null : directoryConflict(targetDirectory);
            if (conflict == null) {
                conflict = fileConflict(renames);
            }
            return conflict == null ? plan(targetDirectory, renames, Status.RENAME, null)
                    : plan(targetDirectory, renames, Status.CONFLICT, conflict);
        }

        /** Nur Groß- und Kleinschreibung zu ändern ist kein Konflikt, auch wenn Windows den Ordner findet. */
        private String directoryConflict(Path targetDirectory) {
            boolean sameIgnoringCase = targetDirectory.toString().toLowerCase(Locale.ROOT)
                    .equals(currentDirectory.toString().toLowerCase(Locale.ROOT));
            if (!sameIgnoringCase && Files.exists(targetDirectory)) {
                return "Der Ordner „%s“ existiert bereits.".formatted(targetDirectory);
            }
            if (targetDirectory.startsWith(currentDirectory) && !sameIgnoringCase) {
                return "Der neue Ordner liegt im bisherigen.";
            }
            return null;
        }

        private String fileConflict(List<FileRename> renames) {
            Set<String> renamed = Set.copyOf(renames.stream()
                    .map(rename -> rename.currentName().toLowerCase(Locale.ROOT)).toList());
            for (FileRename rename : renames) {
                String target = rename.targetName();
                boolean freed = renamed.contains(target.toLowerCase(Locale.ROOT));
                if (!freed && Files.exists(currentDirectory.resolve(target))) {
                    return "Die Datei „%s“ existiert bereits.".formatted(target);
                }
            }
            return null;
        }

        private MovieRenamePlan plan(Path targetDirectory, List<FileRename> renames, Status status, String message) {
            return new MovieRenamePlan(candidate.movieId(), candidate.title(), currentDirectory.toString(),
                    targetDirectory.toString(), renames, status, message);
        }
    }
}
