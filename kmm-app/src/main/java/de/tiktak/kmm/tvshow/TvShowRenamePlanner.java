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

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.scanner.naming.CompanionNames;
import de.tiktak.kmm.scanner.naming.NamePattern;
import de.tiktak.kmm.tvshow.TvShowRenamePlan.EpisodeFile;
import de.tiktak.kmm.tvshow.TvShowRenamePlan.FileRename;
import de.tiktak.kmm.tvshow.TvShowRenamePlan.Status;

/**
 * Computes what a TV show would be named after the patterns, without changing anything. Like Ember:
 * <ul>
 * <li>Each episode file moves with its companion files (NFO, thumbnail, subtitles …) into the season folder
 * of the pattern below the show folder; if the pattern yields nothing, into the show folder itself.</li>
 * <li>Companion files are the files in the same folder whose name starts with that of the video file, followed by
 * a dot, hyphen, underscore or space.</li>
 * <li>The show folder is renamed unless it is the source itself; a pattern without {@code /} renames it in place,
 * one with {@code /} puts it relative to the source.</li>
 * <li>Season artwork lives in the show folder by Kodi convention and stays where it is.</li>
 * <li>Taken targets are conflicts: for the show folder for the whole show, otherwise for the episode file.</li>
 * </ul>
 */
final class TvShowRenamePlanner {

    private TvShowRenamePlanner() {
    }

    static TvShowRenamePlan plan(Candidate candidate, Patterns patterns) {
        Path showDirectory = candidate.showDirectory();
        Path target = showDirectory;
        if (candidate.ownsFolder()) {
            List<String> segments = patterns.showFolder().render(candidate.values()::value);
            if (segments.isEmpty()) {
                return candidate.result(target, List.of(), Status.FAILED,
                        LocalizedText.of("tvShow.rename.showFolderEmpty"));
            }
            Path base = patterns.showFolder().hasFolders() ? candidate.sourceRoot() : showDirectory.getParent();
            target = resolve(base, segments);
        }
        List<EpisodeFile> files;
        try {
            files = withSharedTargetsAsConflicts(episodeFiles(candidate, patterns));
        } catch (UncheckedIOException e) {
            return candidate.result(showDirectory, List.of(), Status.FAILED,
                    LocalizedText.of("tvShow.rename.folderUnreadable").with("reason", e.getCause().getMessage()));
        }
        LocalizedText folderConflict = folderConflict(showDirectory, target);
        if (folderConflict != null) {
            return candidate.result(target, files, Status.CONFLICT, folderConflict);
        }
        boolean renames = !target.toString().equals(showDirectory.toString())
                || files.stream().anyMatch(file -> file.status() == Status.RENAME);
        if (renames) {
            return candidate.result(target, files, Status.RENAME, null);
        }
        return files.isEmpty() ? candidate.result(target, files, Status.UNCHANGED, null)
                : candidate.result(target, files, Status.CONFLICT, LocalizedText.of("tvShow.rename.noEpisodeFile"));
    }

    private static List<EpisodeFile> episodeFiles(Candidate candidate, Patterns patterns) {
        Path showDirectory = candidate.showDirectory();
        Map<Path, List<String>> listings = new HashMap<>();
        Map<Path, Set<String>> baseNamesByFolder = candidate.files().stream()
                .collect(Collectors.groupingBy(file -> file.file().getParent(),
                        Collectors.mapping(file -> CompanionNames.baseName(file.file()), Collectors.toSet())));
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
                        LocalizedText.of("tvShow.rename.episodeFileInvalid")));
                continue;
            }
            String base = CompanionNames.baseName(file.file());
            Set<String> others = baseNamesByFolder.get(folder);
            List<FileRename> renames = listing(listings, folder).stream()
                    .filter(candidateName -> CompanionNames.belongsOnlyTo(candidateName, base, others))
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

    /** A target is free if it does not exist or is renamed itself (even if only in its case). */
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
                        Status.CONFLICT, LocalizedText.of("tvShow.rename.fileExists").with("file", rename.targetName()));
            }
        }
        return file;
    }

    /** Two episode files must not get the same name. */
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
                        Status.CONFLICT, LocalizedText.of("tvShow.rename.sameName"))
                : file).toList();
    }

    private static LocalizedText folderConflict(Path current, Path target) {
        boolean sameIgnoringCase = target.toString().toLowerCase(Locale.ROOT)
                .equals(current.toString().toLowerCase(Locale.ROOT));
        if (sameIgnoringCase) {
            return null;
        }
        if (Files.exists(target)) {
            return LocalizedText.of("tvShow.rename.folderExists").with("folder", target);
        }
        return target.startsWith(current) ? LocalizedText.of("tvShow.rename.folderInside") : null;
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
     * A TV show as the planner needs it.
     *
     * @param ownsFolder whether the show folder is not the source itself
     * @param files      the episode files with their episodes in order
     */
    record Candidate(long showId, String title, Path showDirectory, Path sourceRoot, boolean ownsFolder,
                     TvNameValues values, List<CandidateFile> files) {

        TvShowRenamePlan result(Path target, List<EpisodeFile> files, Status status, LocalizedText message) {
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
