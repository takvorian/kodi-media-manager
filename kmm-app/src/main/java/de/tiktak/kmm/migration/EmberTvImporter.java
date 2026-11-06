package de.tiktak.kmm.migration;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.job.JobContext;
import de.tiktak.kmm.migration.ember.library.EmberEpisode;
import de.tiktak.kmm.migration.ember.library.EmberTvLibrary;
import de.tiktak.kmm.migration.ember.library.EmberTvShow;
import de.tiktak.kmm.migration.ember.library.EmberTvShowSource;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.scanner.EpisodeNameParser;
import de.tiktak.kmm.scanner.EpisodeNumber;
import de.tiktak.kmm.tvshow.TvImportResult;
import de.tiktak.kmm.tvshow.TvShowImportService;
import de.tiktak.kmm.tvshow.TvShowImportService.ImportedTvShow;
import de.tiktak.kmm.tvshow.TvShowImportService.TvShowImportSession;
import lombok.RequiredArgsConstructor;

/**
 * Transfers the TV show library from Ember into the KMM database: TV show sources, TV shows with season titles and
 * episodes with a file. Episodes whose number does not match the file name are skipped. Runs in the caller's
 * transaction so that a dry run can roll it back.
 */
@Component
@RequiredArgsConstructor
class EmberTvImporter {

    static final String DEFAULT_SOURCE_NAME = "Serien";
    private static final String EPISODE_CODE = "S%02dE%02d";
    private static final char EXTENSION_SEPARATOR = '.';

    private final EmberSourceImporter sourceImporter;
    private final TvShowImportService importService;
    private final Clock clock;
    private final EpisodeNameParser episodeNameParser = new EpisodeNameParser();

    void importLibrary(EmberTvLibrary library, EmberPathTranslator paths, EmberImportProgress progress,
            JobContext context) {
        Map<Long, Long> sourceIds = importSources(library.sources(), paths, progress);
        Map<EmberTvShow, String> directories = new LinkedHashMap<>();
        for (EmberTvShow show : library.shows()) {
            Optional<String> directory = sourceIds.containsKey(show.sourceId()) && show.path() != null
                    ? paths.translate(show.path())
                    : Optional.empty();
            directory.ifPresentOrElse(path -> directories.put(show, path), progress::showSkipped);
        }
        Map<Long, List<EmberEpisode>> episodes = episodesByShow(library.episodes());
        TvShowImportSession session = importService.startSession(directories.values());
        int done = 0;
        for (Map.Entry<EmberTvShow, String> entry : directories.entrySet()) {
            context.throwIfCancellationRequested();
            EmberTvShow show = entry.getKey();
            context.reportProgress(done++, directories.size(), EmberTvShowMapper.displayName(show));
            List<EmberEpisode> showEpisodes = episodes.getOrDefault(show.id(), List.of());
            Optional<ImportedTvShow> imported = importShow(session, show, sourceIds.get(show.sourceId()),
                    entry.getValue(), progress);
            imported.ifPresentOrElse(target -> importEpisodes(session, target, showEpisodes, paths, progress),
                    () -> showEpisodes.forEach(episode -> progress.episodeSkipped()));
            episodes.remove(show.id());
        }
        episodes.values().forEach(skipped -> skipped.forEach(episode -> progress.episodeSkipped()));
        progress.episodesMissing(library.episodesWithoutFile());
        context.reportProgress(directories.size(), directories.size());
    }

    private Map<Long, Long> importSources(List<EmberTvShowSource> sources, EmberPathTranslator paths,
            EmberImportProgress progress) {
        Map<Long, Long> sourceIds = new HashMap<>();
        for (EmberTvShowSource source : sources) {
            String name = source.name() == null ? DEFAULT_SOURCE_NAME : source.name();
            sourceImporter.importSource(name, source.path(), source.language(), MediaType.TV_SHOW, paths, progress)
                    .ifPresent(id -> sourceIds.put(source.id(), id));
        }
        return sourceIds;
    }

    private Optional<ImportedTvShow> importShow(TvShowImportSession session, EmberTvShow show, long sourceId,
            String directory, EmberImportProgress progress) {
        try {
            ImportedTvShow imported = session.importShow(EmberTvShowMapper.toImport(show, sourceId, directory));
            progress.showImported(imported.result().created());
            return Optional.of(imported);
        } catch (InvalidPathException e) {
            progress.showSkipped();
            progress.warn(LocalizedText.of("migration.warning.invalidPath")
                    .with("title", EmberTvShowMapper.displayName(show)).with("path", directory));
            return Optional.empty();
        }
    }

    private void importEpisodes(TvShowImportSession session, ImportedTvShow show, List<EmberEpisode> episodes,
            EmberPathTranslator paths, EmberImportProgress progress) {
        for (EmberEpisode episode : episodes) {
            Optional<String> filePath = paths.translate(episode.path());
            if (filePath.isEmpty()) {
                progress.episodeSkipped();
                continue;
            }
            Optional<EpisodeNumber> fromFileName = numberInFileName(filePath.get());
            if (fromFileName.isPresent() && !describes(fromFileName.get(), episode)) {
                progress.episodeSkipped();
                progress.warn(LocalizedText.of("migration.warning.episodeMismatch").with("path", episode.path())
                        .with("ember", EPISODE_CODE.formatted(episode.season(), episode.episode()))
                        .with("file", EPISODE_CODE.formatted(fromFileName.get().season(),
                                fromFileName.get().episodes().getFirst())));
                continue;
            }
            try {
                TvImportResult result = session.importEpisode(show,
                        EmberTvShowMapper.toImport(episode, filePath.get(), clock.getZone()));
                progress.episodeImported(result.created());
            } catch (InvalidPathException e) {
                progress.episodeSkipped();
            }
        }
    }

    /** Ember misread some file names, e.g. episode 720 from {@code s01e02-720p}. */
    private Optional<EpisodeNumber> numberInFileName(String filePath) {
        String fileName = Path.of(filePath).getFileName().toString();
        int extensionStart = fileName.lastIndexOf(EXTENSION_SEPARATOR);
        return episodeNameParser.parse(extensionStart > 0 ? fileName.substring(0, extensionStart) : fileName);
    }

    private static boolean describes(EpisodeNumber number, EmberEpisode episode) {
        return number.season() == episode.season() && number.episodes().contains(episode.episode());
    }

    private static Map<Long, List<EmberEpisode>> episodesByShow(List<EmberEpisode> episodes) {
        Map<Long, List<EmberEpisode>> byShow = new HashMap<>();
        episodes.forEach(episode -> byShow.computeIfAbsent(episode.showId(), key -> new ArrayList<>()).add(episode));
        return byShow;
    }
}
