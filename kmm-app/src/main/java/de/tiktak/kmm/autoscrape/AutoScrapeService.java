package de.tiktak.kmm.autoscrape;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.web.ErrorResponseException;

import de.tiktak.kmm.artwork.ArtworkFillResult;
import de.tiktak.kmm.artwork.ArtworkService;
import de.tiktak.kmm.job.JobContext;
import de.tiktak.kmm.job.JobService;
import de.tiktak.kmm.job.JobSnapshot;
import de.tiktak.kmm.mediainfo.StreamDetailsService;
import de.tiktak.kmm.movie.MovieMetadataService;
import de.tiktak.kmm.movie.MovieQueryService;
import de.tiktak.kmm.movie.MovieEnrichmentService;
import de.tiktak.kmm.movie.MovieRenamePlan;
import de.tiktak.kmm.movie.MovieRenameService;
import de.tiktak.kmm.movie.MovieScrapeService;
import de.tiktak.kmm.movie.MovieTrailerService;
import de.tiktak.kmm.movie.RenameTrigger;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.source.MediaSource;
import de.tiktak.kmm.theme.ThemeService;
import de.tiktak.kmm.source.MediaSourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Scrapt alle Filme bzw. Serien einer Quelle, einen einzelnen Eintrag oder eine Auswahl im Hintergrund: fehlende Metadaten (nur bei sicherem
 * Treffer), fehlende Bilder und fehlende Trailer-Links. Vorhandene Daten und Bilder bleiben unverändert. Die Einträge werden
 * nacheinander bearbeitet, um die Anfragelimits der Anbieter zu schonen.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoScrapeService {

    /** Präfix des Job-Schlüssels; pro Quelle läuft höchstens ein automatisches Scrapen. */
    public static final String JOB_KEY_PREFIX = "auto-scrape:";
    /** Präfix des Job-Schlüssels für einen einzelnen Film; je Film läuft höchstens ein Scrapen. */
    public static final String MOVIE_JOB_KEY_PREFIX = "movie-scrape:";
    /** Präfix des Job-Schlüssels für eine einzelne Serie; je Serie läuft höchstens ein Scrapen. */
    public static final String TV_SHOW_JOB_KEY_PREFIX = "tvshow-scrape:";
    /** Job-Schlüssel für eine Auswahl von Filmen; es läuft höchstens eine. */
    public static final String MOVIES_JOB_KEY = "movies-scrape";
    /** Job-Schlüssel für eine Auswahl von Serien; es läuft höchstens eine. */
    public static final String TV_SHOWS_JOB_KEY = "tvshows-scrape";
    static final String SINGLE_TITLE = "Scrapen „%s“";
    static final String ONE_MOVIE_TITLE = "1 Film scrapen";
    static final String MOVIES_TITLE = "%d Filme scrapen";
    static final String ONE_TV_SHOW_TITLE = "1 Serie scrapen";
    static final String TV_SHOWS_TITLE = "%d Serien scrapen";
    static final String SEASON_TITLE = "Staffel %d von „%s“ scrapen";
    static final String SEASON_REFETCH_TITLE = "Staffel %d von „%s“ neu holen";
    static final String UNKNOWN_MOVIE = "Film %d";
    static final String NO_METADATA_SCRAPER = "Kein Metadaten-Scraper ist eingerichtet; es werden nur Bilder geholt.";
    static final String NOT_RENAMED = "„%s“ nicht umbenannt: %s";
    private static final String UNEXPECTED_ERROR = "unerwarteter Fehler";
    static final String TECHNICAL_DETAILS = "Technische Angaben: ";
    static final String THEME = "Titelmusik: ";
    static final String ENRICHMENT = "Ergänzen: ";

    private final MediaSourceService sourceService;
    private final MovieQueryService queryService;
    private final MovieMetadataService metadataService;
    private final MovieScrapeService scrapeService;
    private final MovieRenameService renameService;
    private final ArtworkService artworkService;
    private final MovieTrailerService trailerService;
    private final ThemeService themeService;
    private final MovieEnrichmentService enrichmentService;
    private final JobService jobService;
    private final TvAutoScrapeRunner tvRunner;
    private final StreamDetailsService streamDetails;

    /**
     * @throws AutoScrapeNotPossibleException wenn die Quelle weder Filme noch Serien enthält
     */
    public JobSnapshot start(long sourceId) {
        MediaSource source = sourceService.require(sourceId);
        String title = SINGLE_TITLE.formatted(source.getName());
        return switch (source.getMediaType()) {
            case MOVIE -> jobService.submit(JOB_KEY_PREFIX + sourceId, title, context -> run(sourceId, context));
            case TV_SHOW -> jobService.submit(JOB_KEY_PREFIX + sourceId, title,
                    context -> tvRunner.run(sourceId, context));
            default -> throw new AutoScrapeNotPossibleException(source);
        };
    }

    /**
     * Scrapt einen einzelnen Film.
     *
     * @throws de.tiktak.kmm.movie.MovieNotFoundException wenn es den Film nicht gibt
     */
    public JobSnapshot startMovie(long movieId) {
        String title = metadataService.findDetails(movieId).metadata().title();
        return jobService.submit(MOVIE_JOB_KEY_PREFIX + movieId, SINGLE_TITLE.formatted(title),
                context -> scrapeMovies(List.of(movieId), context));
    }

    /** Scrapt die ausgewählten Filme nacheinander. */
    public JobSnapshot startMovies(List<Long> movieIds) {
        List<Long> distinct = List.copyOf(new LinkedHashSet<>(movieIds));
        return jobService.submit(MOVIES_JOB_KEY,
                distinct.size() == 1 ? ONE_MOVIE_TITLE : MOVIES_TITLE.formatted(distinct.size()),
                context -> scrapeMovies(distinct, context));
    }

    /**
     * Scrapt eine einzelne Serie.
     *
     * @throws org.springframework.web.ErrorResponseException wenn es die Serie nicht gibt
     */
    public JobSnapshot startTvShow(long showId) {
        return jobService.submit(TV_SHOW_JOB_KEY_PREFIX + showId, SINGLE_TITLE.formatted(tvRunner.title(showId)),
                context -> tvRunner.run(List.of(showId), context));
    }

    /**
     * Scrapt eine Staffel einer Serie. Sie teilt sich den Job-Schlüssel mit der Serie, damit beide nicht gleichzeitig
     * laufen.
     *
     * @param overwrite ob Titel und Texte der Episoden durch die des Anbieters ersetzt werden
     * @throws AutoScrapeNotPossibleException wenn die Staffel keine Episoden hat
     */
    public JobSnapshot startTvSeason(long showId, int season, boolean overwrite) {
        String title = tvRunner.requireSeason(showId, season);
        return jobService.submit(TV_SHOW_JOB_KEY_PREFIX + showId,
                (overwrite ? SEASON_REFETCH_TITLE : SEASON_TITLE).formatted(season, title),
                context -> tvRunner.runSeason(showId, season, overwrite, context));
    }

    /** Scrapt die ausgewählten Serien nacheinander. */
    public JobSnapshot startTvShows(List<Long> showIds) {
        List<Long> distinct = List.copyOf(new LinkedHashSet<>(showIds));
        return jobService.submit(TV_SHOWS_JOB_KEY,
                distinct.size() == 1 ? ONE_TV_SHOW_TITLE : TV_SHOWS_TITLE.formatted(distinct.size()),
                context -> tvRunner.run(distinct, context));
    }

    AutoScrapeResult run(long sourceId, JobContext context) {
        return scrapeMovies(queryService.findMovieIds(sourceId), context);
    }

    private AutoScrapeResult scrapeMovies(List<Long> movieIds, JobContext context) {
        AutoScrapeProgress progress = new AutoScrapeProgress(movieIds.size());
        for (int index = 0; index < movieIds.size(); index++) {
            context.throwIfCancellationRequested();
            long movieId = movieIds.get(index);
            Optional<String> title = findTitle(movieId, progress);
            context.reportProgress(index, movieIds.size(), title.orElse(null));
            title.ifPresent(existing -> scrapeMovie(movieId, existing, progress));
        }
        context.reportProgress(movieIds.size(), movieIds.size());
        AutoScrapeResult result = progress.toResult();
        log.info("Auto-scraped {} movies: {} scraped, {} unmatched, {} images, {} trailers, {} themes, "
                + "{} failures", result.movies(), result.scraped(), result.unmatched(), result.imagesSaved(),
                result.trailersSaved(), result.themesSaved(), result.failures().size());
        return result;
    }

    /** Ein Eintrag der Auswahl kann inzwischen entfernt worden sein; das hält die übrigen nicht auf. */
    private Optional<String> findTitle(long movieId, AutoScrapeProgress progress) {
        try {
            return Optional.of(metadataService.findDetails(movieId).metadata().title());
        } catch (ErrorResponseException e) {
            progress.failed(UNKNOWN_MOVIE.formatted(movieId), e.getBody().getDetail());
            return Optional.empty();
        }
    }

    private void scrapeMovie(long movieId, String title, AutoScrapeProgress progress) {
        try {
            switch (scrapeService.scrapeMissingMetadata(movieId)) {
                case SCRAPED -> {
                    progress.scraped();
                    streamDetails.probeMovieAfterScrape(movieId)
                            .ifPresent(failure -> progress.failed(title, TECHNICAL_DETAILS + failure));
                }
                case NO_MATCH -> progress.unmatched(movieId, title);
                case NO_SCRAPER -> progress.warn(NO_METADATA_SCRAPER);
                case ALREADY_IDENTIFIED -> {
                    // Gepflegte Metadaten bleiben unverändert.
                }
            }
            ArtworkFillResult artwork = artworkService.fillMissing(movieId);
            progress.imagesSaved(artwork.saved());
            artwork.warnings().forEach(progress::warn);
            if (trailerService.fillMissing(movieId)) {
                progress.trailerSaved();
            }
            fillTheme(movieId, title, progress);
            enrich(movieId, title, progress);
            renameService.renameIfEnabled(movieId, RenameTrigger.SCRAPE)
                    .filter(plan -> plan.status() == MovieRenamePlan.Status.CONFLICT
                            || plan.status() == MovieRenamePlan.Status.FAILED)
                    .ifPresent(plan -> progress.warn(NOT_RENAMED.formatted(title, plan.message())));
        } catch (ErrorResponseException e) {
            progress.failed(title, e.getBody().getDetail());
        } catch (RuntimeException e) {
            log.warn("Auto-scraping movie {} failed", movieId, e);
            progress.failed(title, UNEXPECTED_ERROR);
        }
    }

    /** Fehlende Bewertungen und leere Felder (etwa Trakt, OMDb); ein Fehler dabei hält den Rest nicht auf. */
    private void enrich(long movieId, String title, AutoScrapeProgress progress) {
        try {
            if (enrichmentService.fillMissing(movieId)) {
                progress.enriched();
            }
        } catch (ErrorResponseException e) {
            progress.failed(title, ENRICHMENT + e.getBody().getDetail());
        }
    }

    /** Fehlende Titelmusik bei sicherem Treffer; ein Fehler dabei hält den Rest nicht auf. */
    private void fillTheme(long movieId, String title, AutoScrapeProgress progress) {
        try {
            if (themeService.fillMovie(movieId)) {
                progress.themeSaved();
            }
        } catch (ErrorResponseException e) {
            progress.failed(title, THEME + e.getBody().getDetail());
        }
    }
}
