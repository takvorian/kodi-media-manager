package de.tiktak.kmm.migration;

import java.nio.file.InvalidPathException;
import java.time.Clock;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import de.tiktak.kmm.job.JobContext;
import de.tiktak.kmm.migration.ember.library.EmberLibrary;
import de.tiktak.kmm.migration.ember.library.EmberMovie;
import de.tiktak.kmm.migration.ember.library.EmberMovieSet;
import de.tiktak.kmm.migration.ember.library.EmberMovieSource;
import de.tiktak.kmm.migration.ember.library.EmberTvLibrary;
import de.tiktak.kmm.migration.ember.settings.EmberSettings;
import de.tiktak.kmm.movie.MovieImportResult;
import de.tiktak.kmm.movie.MovieImportService;
import de.tiktak.kmm.movie.MovieSetService;
import de.tiktak.kmm.movie.SetSortMethod;
import de.tiktak.kmm.movie.MovieImportService.MovieImportSession;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.settings.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Überträgt einen gelesenen Ember-Bestand in die KMM-Datenbank: Quellen, Filme mit allen Angaben,
 * Sets und auf Wunsch Serien und die Einstellungen. Läuft in der Transaktion des Aufrufers, damit ein Probelauf
 * sie zurückrollen kann.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class EmberLibraryImporter {

    static final String DEFAULT_SOURCE_NAME = "Filme";

    private final EmberSourceImporter sourceImporter;
    private final EmberTvImporter tvImporter;
    private final MovieImportService movieImportService;
    private final MovieSetService movieSetService;
    private final SettingsService settingsService;
    private final Clock clock;

    /**
     * @param tvLibrary Serienbestand; leer, wenn keine Serien übernommen werden sollen
     */
    void importLibrary(EmberLibrary library, Optional<EmberTvLibrary> tvLibrary, Optional<EmberSettings> settings,
            EmberImportRequest request, EmberImportProgress progress, JobContext context) {
        EmberPathTranslator paths = EmberPathTranslator.of(request.pathMappings());
        Map<Long, Long> sourceIds = importSources(library.sources(), paths, progress);
        importMovies(library, sourceIds, paths, progress, context);
        tvLibrary.ifPresent(tv -> tvImporter.importLibrary(tv, paths, progress, context));
        if (request.importSettings()) {
            importSettings(settings, library.excludedDirectories(), paths, progress, request.dryRun());
        }
    }

    private Map<Long, Long> importSources(List<EmberMovieSource> sources, EmberPathTranslator paths,
            EmberImportProgress progress) {
        Map<Long, Long> sourceIds = new HashMap<>();
        for (EmberMovieSource source : sources) {
            String name = source.name() == null ? DEFAULT_SOURCE_NAME : source.name();
            sourceImporter.importSource(name, source.path(), MediaType.MOVIE, paths, progress)
                    .ifPresent(id -> sourceIds.put(source.id(), id));
        }
        return sourceIds;
    }

    private void importMovies(EmberLibrary library, Map<Long, Long> sourceIds, EmberPathTranslator paths,
            EmberImportProgress progress, JobContext context) {
        Map<EmberMovie, String> filePaths = new LinkedHashMap<>();
        for (EmberMovie movie : library.movies()) {
            Optional<String> filePath = sourceIds.containsKey(movie.sourceId()) && movie.path() != null
                    ? paths.translate(movie.path())
                    : Optional.empty();
            filePath.ifPresentOrElse(path -> filePaths.put(movie, path), progress::movieSkipped);
        }
        Map<Long, EmberMovieSet> sets = library.setsById();
        importSets(library.sets());
        MovieImportSession session = movieImportService.startSession(filePaths.values());
        int done = 0;
        for (Map.Entry<EmberMovie, String> entry : filePaths.entrySet()) {
            context.throwIfCancellationRequested();
            EmberMovie movie = entry.getKey();
            context.reportProgress(done++, filePaths.size(), EmberMovieMapper.displayName(movie));
            MovieImportResult result;
            try {
                result = session.importMovie(
                        EmberMovieMapper.toImport(movie, sourceIds.get(movie.sourceId()), entry.getValue(), sets,
                                clock.getZone()));
            } catch (InvalidPathException e) {
                progress.movieSkipped();
                progress.warn("„%s“ wurde übersprungen, der Pfad ist auf diesem System ungültig: %s"
                        .formatted(EmberMovieMapper.displayName(movie), entry.getValue()));
                continue;
            }
            progress.movieImported(result.created());
            if (!result.nfoPresent()) {
                progress.nfoMissing(EmberMovieMapper.displayName(movie));
            }
            if (EmberMovieMapper.belongsToSeveralSets(movie)) {
                progress.warn("„%s“ gehört in Ember zu mehreren Sets; übernommen wurde das erste."
                        .formatted(EmberMovieMapper.displayName(movie)));
            }
        }
        context.reportProgress(filePaths.size(), filePaths.size(), null);
    }

    /** Alle Sets, auch solche ohne Filme, mit Beschreibung, TMDB-ID und Sortierung. */
    private void importSets(List<EmberMovieSet> sets) {
        sets.stream()
                .filter(set -> set.title() != null)
                .forEach(set -> movieSetService.importSet(set.title(), set.plot(), set.tmdbCollectionId(),
                        set.sortedByTitle() ? SetSortMethod.TITLE : SetSortMethod.YEAR));
    }

    private void importSettings(Optional<EmberSettings> settings, List<String> excludedDirectories,
            EmberPathTranslator paths, EmberImportProgress progress, boolean dryRun) {
        List<String> excluded = excludedDirectories.stream()
                .map(paths::translate)
                .flatMap(Optional::stream)
                .toList();
        var imported = EmberSettingsMapper.map(settingsService.current(), settings, excluded);
        progress.settingsChanged(imported.changes());
        if (!dryRun && !imported.changes().isEmpty()) {
            settingsService.update(imported.settings());
        }
    }
}
