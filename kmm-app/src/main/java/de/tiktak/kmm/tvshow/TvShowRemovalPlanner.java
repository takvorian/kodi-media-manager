package de.tiktak.kmm.tvshow;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.removal.RemovalFiles;
import de.tiktak.kmm.removal.RemovalProblemException;
import de.tiktak.kmm.scanner.TvShowFiles;
import de.tiktak.kmm.scanner.naming.CompanionNames;
import de.tiktak.kmm.source.MediaSource;
import de.tiktak.kmm.source.MediaSourceService;
import de.tiktak.kmm.tvshow.EpisodesRemovedEvent.RemovedEpisode;
import lombok.RequiredArgsConstructor;

/**
 * Works out what removing an episode, a season or a TV show comprises, without changing anything:
 * <ul>
 * <li>Episode: the video file with its companion files (NFO, thumbnail, subtitles …). A multi-episode file is always
 * removed as a whole, with all its episodes.</li>
 * <li>Season: all episode files of the season as above, plus the season images in the TV show folder.</li>
 * <li>TV show: the whole TV show folder.</li>
 * </ul>
 * Empty folders are cleaned up up to the TV show folder, for a whole TV show up to the source.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
class TvShowRemovalPlanner {

    private static final String SEASON_CODE = "S%02d";
    private static final String EPISODE_NUMBER = "E%02d";
    private static final Set<String> SHOW_FOLDER_FILE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "nfo");
    private static final String THEME_PREFIX = "theme.";

    private final TvShowRepository showRepository;
    private final EpisodeRepository episodeRepository;
    private final MediaSourceService sourceService;

    /**
     * @throws TvShowProblemException if the episode does not exist
     */
    TvShowRemoval episode(long episodeId) {
        Episode episode = episodeRepository.findById(episodeId)
                .orElseThrow(() -> TvShowProblemException.episodeNotFound(episodeId));
        TvShow show = requireShow(episode.getTvShowId());
        return episodes(show, List.of(episode), null);
    }

    /**
     * @throws TvShowProblemException  if the TV show does not exist
     * @throws RemovalProblemException if the season has no episodes
     */
    TvShowRemoval season(long showId, int season) {
        TvShow show = requireShow(showId);
        List<Episode> selected = episodeRepository.findByTvShowIdOrderBySeasonAscEpisodeAsc(showId).stream()
                .filter(episode -> episode.getSeason() == season)
                .toList();
        if (selected.isEmpty()) {
            throw RemovalProblemException.seasonNotFound(show.getTitle(), season);
        }
        return episodes(show, selected, season);
    }

    /**
     * @throws TvShowProblemException if the TV show does not exist
     */
    TvShowRemoval show(long showId) {
        TvShow show = requireShow(showId);
        List<Episode> episodes = episodeRepository.findByTvShowIdOrderBySeasonAscEpisodeAsc(showId);
        MediaSource source = sourceService.require(show.getSourceId());
        Path root = Path.of(source.getPath());
        RemovalFiles files = new RemovalFiles();
        List<Path> content = files.scope(root, source.getName(), root).addDirectory(show.directory());
        List<Path> unknown = unknownFiles(show.directory(), content, episodes);
        if (!unknown.isEmpty()) {
            files.note(LocalizedText.of("removal.note.unknownFiles").with("count", unknown.size())
                    .with("files", RemovalFiles.listing(unknown)));
        }
        LocalizedText entry = LocalizedText.of("removal.entry.tvShow").with("title", show.getTitle())
                .with("count", episodes.size());
        return new TvShowRemoval(showId, show.getTitle(), show.getDirectoryPath(), show.getTitle(),
                episodes.stream().map(Episode::getId).toList(), references(episodes), seasons(episodes), true, files,
                List.of(entry));
    }

    /**
     * @param season the season being removed, or {@code null} for a single episode
     */
    private TvShowRemoval episodes(TvShow show, List<Episode> selected, Integer season) {
        List<Episode> all = episodeRepository.findByTvShowIdOrderBySeasonAscEpisodeAsc(show.getId());
        Set<String> paths = selected.stream().map(Episode::getFilePath).collect(Collectors.toCollection(TreeSet::new));
        List<Episode> removed = all.stream().filter(episode -> paths.contains(episode.getFilePath())).toList();
        MediaSource source = sourceService.require(show.getSourceId());
        RemovalFiles files = new RemovalFiles();
        RemovalFiles.Scope scope = files.scope(Path.of(source.getPath()), source.getName(), show.directory());
        Map<Path, Set<String>> baseNamesByFolder = all.stream().map(Episode::file).distinct()
                .collect(Collectors.groupingBy(Path::getParent,
                        Collectors.mapping(CompanionNames::baseName, Collectors.toSet())));
        for (String path : paths) {
            Path file = Path.of(path);
            Set<String> others = new HashSet<>(baseNamesByFolder.getOrDefault(file.getParent(), Set.of()));
            others.remove(CompanionNames.baseName(file));
            scope.addWithCompanions(file, others);
            noteSharedFile(files, file, removed.stream().filter(episode -> episode.getFilePath().equals(path))
                    .toList(), season);
        }
        if (season != null) {
            addSeasonArtwork(files, scope, show.directory(), season);
        }
        List<LocalizedText> entries = removed.stream().map(episode -> entry(show, episode)).toList();
        String subject = season == null ? show.getTitle() + " " + code(removed) : show.getTitle();
        return new TvShowRemoval(show.getId(), show.getTitle(), show.getDirectoryPath(), subject,
                removed.stream().map(Episode::getId).toList(), references(removed), seasons(removed), false, files,
                entries);
    }

    private static void noteSharedFile(RemovalFiles files, Path file, List<Episode> fileEpisodes, Integer season) {
        if (fileEpisodes.size() > 1) {
            files.note(LocalizedText.of("removal.note.multiEpisode").with("file", file.getFileName()));
        }
        if (season != null) {
            fileEpisodes.stream().map(Episode::getSeason).filter(other -> !season.equals(other)).distinct()
                    .forEach(other -> files.note(LocalizedText.of("removal.note.otherSeason")
                            .with("file", file.getFileName()).with("season", other)));
        }
    }

    private static void addSeasonArtwork(RemovalFiles files, RemovalFiles.Scope scope, Path showDirectory,
                                         int season) {
        try {
            scope.addFiles(TvShowFiles.seasonArtworkFiles(showDirectory, season));
        } catch (IOException e) {
            files.unreadable(showDirectory, e);
        }
    }

    /**
     * Files in the TV show folder that are neither episode files with their companions nor images, NFOs or theme music
     * of the TV show and its seasons directly in the TV show folder.
     */
    private static List<Path> unknownFiles(Path showDirectory, List<Path> content, List<Episode> episodes) {
        Map<Path, Set<String>> baseNamesByFolder = episodes.stream().map(Episode::file).distinct()
                .collect(Collectors.groupingBy(Path::getParent,
                        Collectors.mapping(CompanionNames::baseName, Collectors.toSet())));
        return content.stream()
                .filter(file -> !isShowFolderFile(showDirectory, file))
                .filter(file -> baseNamesByFolder.getOrDefault(file.getParent(), Set.of()).stream()
                        .noneMatch(baseName -> CompanionNames.belongsTo(file.getFileName().toString(), baseName)))
                .toList();
    }

    private static boolean isShowFolderFile(Path showDirectory, Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        int extensionStart = name.lastIndexOf('.');
        return file.getParent().equals(showDirectory) && (name.startsWith(THEME_PREFIX)
                || extensionStart > 0 && SHOW_FOLDER_FILE_EXTENSIONS.contains(name.substring(extensionStart + 1)));
    }

    private TvShow requireShow(long showId) {
        return showRepository.findById(showId).orElseThrow(() -> TvShowProblemException.showNotFound(showId));
    }

    private static LocalizedText entry(TvShow show, Episode episode) {
        return LocalizedText.of("removal.entry.episode").with("show", show.getTitle())
                .with("code", code(List.of(episode)))
                .with("title", episode.getTitle());
    }

    /** Season and episode numbers as in file names, e.g. {@code S01E01E02} for a multi-episode file. */
    private static String code(List<Episode> episodes) {
        return SEASON_CODE.formatted(episodes.getFirst().getSeason()) + episodes.stream()
                .map(episode -> EPISODE_NUMBER.formatted(episode.getEpisode()))
                .collect(Collectors.joining());
    }

    private static List<RemovedEpisode> references(List<Episode> episodes) {
        return episodes.stream()
                .map(episode -> new RemovedEpisode(episode.getFilePath(), episode.getSeason(), episode.getEpisode()))
                .toList();
    }

    private static Set<Integer> seasons(List<Episode> episodes) {
        return episodes.stream().map(Episode::getSeason).collect(Collectors.toSet());
    }
}
