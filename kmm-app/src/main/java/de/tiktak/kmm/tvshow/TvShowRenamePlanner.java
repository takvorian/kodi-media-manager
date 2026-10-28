package de.tiktak.kmm.tvshow;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import de.tiktak.kmm.scanner.naming.NamePattern;
import de.tiktak.kmm.tvshow.TvShowRenamePlan.EpisodeFile;
import de.tiktak.kmm.tvshow.TvShowRenamePlan.FileRename;
import de.tiktak.kmm.tvshow.TvShowRenamePlan.Status;

/**
 * Berechnet, wie eine Serie nach den Mustern heißen würde, ohne etwas zu ändern. Wie Ember:
 * <ul>
 * <li>Jede Episodendatei wandert mit ihren Begleitdateien (NFO, Vorschaubild, Untertitel …) in den Staffelordner
 * nach Muster unterhalb des Serienordners; ergibt das Muster nichts, in den Serienordner selbst.</li>
 * <li>Begleitdateien sind die Dateien im selben Ordner, deren Name mit dem der Videodatei beginnt, gefolgt von
 * Punkt, Bindestrich, Unterstrich oder Leerzeichen.</li>
 * <li>Der Serienordner wird umbenannt, sofern er nicht die Quelle selbst ist; ein Muster ohne {@code /} benennt
 * ihn an Ort und Stelle um, eines mit {@code /} legt ihn relativ zur Quelle ab.</li>
 * <li>Staffelbilder liegen nach Kodi-Konvention im Serienordner und bleiben, wo sie sind.</li>
 * <li>Belegte Ziele sind Konflikte: beim Serienordner für die ganze Serie, sonst für die Episodendatei.</li>
 * </ul>
 */
final class TvShowRenamePlanner {

    private static final String NAME_SEPARATORS = ".-_ ";
    private static final char EXTENSION_SEPARATOR = '.';

    private TvShowRenamePlanner() {
    }

    static TvShowRenamePlan plan(Candidate candidate, Patterns patterns) {
        Path showDirectory = candidate.showDirectory();
        Path target = showDirectory;
        if (candidate.ownsFolder()) {
            List<String> segments = patterns.showFolder().render(candidate.values()::value);
            if (segments.isEmpty()) {
                return candidate.result(target, List.of(), Status.FAILED, "Das Muster für Serienordner ergibt keinen Namen.");
            }
            Path base = patterns.showFolder().hasFolders() ? candidate.sourceRoot() : showDirectory.getParent();
            target = resolve(base, segments);
        }
        List<EpisodeFile> files;
        try {
            files = withSharedTargetsAsConflicts(episodeFiles(candidate, patterns));
        } catch (UncheckedIOException e) {
            return candidate.result(showDirectory, List.of(), Status.FAILED,
                    "Ein Ordner der Serie ist nicht lesbar: " + e.getCause().getMessage());
        }
        String folderConflict = folderConflict(showDirectory, target);
        if (folderConflict != null) {
            return candidate.result(target, files, Status.CONFLICT, folderConflict);
        }
        boolean renames = !target.toString().equals(showDirectory.toString())
                || files.stream().anyMatch(file -> file.status() == Status.RENAME);
        if (renames) {
            return candidate.result(target, files, Status.RENAME, null);
        }
        return files.isEmpty() ? candidate.result(target, files, Status.UNCHANGED, null)
                : candidate.result(target, files, Status.CONFLICT, "Keine Episodendatei lässt sich umbenennen.");
    }

    private static List<EpisodeFile> episodeFiles(Candidate candidate, Patterns patterns) {
        Path showDirectory = candidate.showDirectory();
        Map<Path, List<String>> listings = new HashMap<>();
        Map<Path, Set<String>> baseNamesByFolder = candidate.files().stream()
                .collect(Collectors.groupingBy(file -> file.file().getParent(),
                        Collectors.mapping(file -> baseName(file.file()), Collectors.toSet())));
        List<EpisodeFile> plans = new ArrayList<>();
        for (CandidateFile file : candidate.files()) {
            TvNameValues values = candidate.values().withEpisodeValues(file.episodes());
            String label = label(file.episodes());
            Path folder = file.file().getParent();
            String currentFolder = showDirectory.relativize(folder).toString();
            List<String> season = patterns.seasonFolder().render(values::value);
            String targetFolder = season.isEmpty() ? "" : season.getFirst();
            List<String> name = patterns.episodeFile().render(values::value);
            if (name.size() != 1) {
                plans.add(new EpisodeFile(label, currentFolder, targetFolder, List.of(), Status.FAILED,
                        "Das Muster für Episodendateien ergibt keinen gültigen Dateinamen."));
                continue;
            }
            String base = baseName(file.file());
            Set<String> others = baseNamesByFolder.get(folder);
            List<FileRename> renames = listing(listings, folder).stream()
                    .filter(candidateName -> belongsTo(candidateName, base) && others.stream().noneMatch(
                            other -> other.length() > base.length() && belongsTo(candidateName, other)))
                    .map(candidateName -> new FileRename(candidateName, name.getFirst()
                            + candidateName.substring(base.length())))
                    .toList();
            boolean moves = !targetFolder.equals(currentFolder);
            boolean renamed = renames.stream().anyMatch(rename -> !rename.currentName().equals(rename.targetName()));
            if (moves || renamed) {
                plans.add(withConflicts(new EpisodeFile(label, currentFolder, targetFolder, renames, Status.RENAME,
                        null), showDirectory, listings));
            }
        }
        return plans;
    }

    /** Ein Ziel ist frei, wenn es nicht existiert oder selbst umbenannt wird (auch nur in der Schreibweise). */
    private static EpisodeFile withConflicts(EpisodeFile file, Path showDirectory, Map<Path, List<String>> listings) {
        Path source = showDirectory.resolve(file.currentFolder());
        Path target = showDirectory.resolve(file.targetFolder());
        Set<String> moving = file.renames().stream()
                .map(rename -> source.resolve(rename.currentName()).toString().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        for (FileRename rename : file.renames()) {
            Path targetFile = target.resolve(rename.targetName());
            if (!moving.contains(targetFile.toString().toLowerCase(Locale.ROOT)) && Files.exists(targetFile)) {
                return new EpisodeFile(file.episodes(), file.currentFolder(), file.targetFolder(), file.renames(),
                        Status.CONFLICT, "Die Datei „%s“ existiert bereits.".formatted(rename.targetName()));
            }
        }
        return file;
    }

    /** Zwei Episodendateien dürfen nicht denselben Namen bekommen. */
    private static List<EpisodeFile> withSharedTargetsAsConflicts(List<EpisodeFile> files) {
        Map<String, Long> counts = files.stream()
                .filter(file -> file.status() == Status.RENAME)
                .flatMap(file -> file.renames().stream()
                        .map(rename -> (file.targetFolder() + "/" + rename.targetName()).toLowerCase(Locale.ROOT)))
                .collect(Collectors.groupingBy(key -> key, Collectors.counting()));
        return files.stream().map(file -> file.status() == Status.RENAME && file.renames().stream()
                .anyMatch(rename -> counts.get((file.targetFolder() + "/" + rename.targetName())
                        .toLowerCase(Locale.ROOT)) > 1)
                ? new EpisodeFile(file.episodes(), file.currentFolder(), file.targetFolder(), file.renames(),
                        Status.CONFLICT, "Mehrere Episodendateien bekämen denselben Namen.")
                : file).toList();
    }

    private static String folderConflict(Path current, Path target) {
        boolean sameIgnoringCase = target.toString().toLowerCase(Locale.ROOT)
                .equals(current.toString().toLowerCase(Locale.ROOT));
        if (sameIgnoringCase) {
            return null;
        }
        if (Files.exists(target)) {
            return "Der Ordner „%s“ existiert bereits.".formatted(target);
        }
        return target.startsWith(current) ? "Der neue Ordner liegt im bisherigen." : null;
    }

    private static List<String> listing(Map<Path, List<String>> listings, Path folder) {
        return listings.computeIfAbsent(folder, directory -> {
            try (Stream<Path> files = Files.list(directory)) {
                return files.filter(Files::isRegularFile).map(file -> file.getFileName().toString()).sorted().toList();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private static boolean belongsTo(String fileName, String baseName) {
        return fileName.equals(baseName) || (fileName.length() > baseName.length()
                && fileName.regionMatches(true, 0, baseName, 0, baseName.length())
                && NAME_SEPARATORS.indexOf(fileName.charAt(baseName.length())) >= 0);
    }

    private static String baseName(Path file) {
        String name = file.getFileName().toString();
        int extension = name.lastIndexOf(EXTENSION_SEPARATOR);
        return extension > 0 ? name.substring(0, extension) : name;
    }

    private static String label(List<TvNameValues.EpisodeValues> episodes) {
        if (episodes.isEmpty()) {
            return "";
        }
        return "S%02d".formatted(episodes.getFirst().season()) + episodes.stream()
                .map(episode -> "E%02d".formatted(episode.episode()))
                .collect(Collectors.joining());
    }

    private static Path resolve(Path base, List<String> segments) {
        Path target = base;
        for (String segment : segments) {
            target = target.resolve(segment);
        }
        return target;
    }

    record Patterns(NamePattern showFolder, NamePattern seasonFolder, NamePattern episodeFile) {
    }

    /**
     * Eine Serie, wie sie der Planer braucht.
     *
     * @param ownsFolder ob der Serienordner nicht die Quelle selbst ist
     * @param files      die Episodendateien mit ihren Episoden in Reihenfolge
     */
    record Candidate(long showId, String title, Path showDirectory, Path sourceRoot, boolean ownsFolder,
                     TvNameValues values, List<CandidateFile> files) {

        TvShowRenamePlan result(Path target, List<EpisodeFile> files, Status status, String message) {
            return new TvShowRenamePlan(showId, title, showDirectory.toString(), target.toString(), files, status,
                    message);
        }
    }

    record CandidateFile(Path file, List<TvNameValues.EpisodeValues> episodes) {

        CandidateFile {
            episodes = List.copyOf(episodes);
        }
    }
}
