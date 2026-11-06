package de.tiktak.kmm.autoscrape;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.web.ErrorResponseException;

import de.tiktak.kmm.artwork.ArtworkFillResult;
import de.tiktak.kmm.artwork.ArtworkService;
import de.tiktak.kmm.i18n.LocalizedText;
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
import de.tiktak.kmm.source.MediaSourceService;
import de.tiktak.kmm.theme.ThemeService;
import de.tiktak.kmm.web.LocalizedTexts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Scrapes all movies or TV shows of a source, a single entry or a selection in the background: missing metadata (only
 * with a certain match), missing images and missing trailer links. Existing data and images stay unchanged. The
 * entries are processed one after the other to spare the request limits of the providers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoScrapeService {

    /** Prefix of the job key; at most one auto-scraping runs per source. */
    public static final String JOB_KEY_PREFIX = "auto-scrape:";
    /** Prefix of the job key for a single movie; at most one scraping runs per movie. */
    public static final String MOVIE_JOB_KEY_PREFIX = "movie-scrape:";
    /** Prefix of the job key for a single TV show; at most one scraping runs per TV show. */
    public static final String TV_SHOW_JOB_KEY_PREFIX = "tvshow-scrape:";
    /** Job key for a selection of movies; at most one runs. */
    public static final String MOVIES_JOB_KEY = "movies-scrape";
    /** Job key for a selection of TV shows; at most one runs. */
    public static final String TV_SHOWS_JOB_KEY = "tvshows-scrape";
    static final String SINGLE_TITLE = "autoScrape.jobTitle.single";
    static final String MOVIES_TITLE = "autoScrape.jobTitle.movies";
    static final String TV_SHOWS_TITLE = "autoScrape.jobTitle.tvShows";
    static final String SEASON_TITLE = "autoScrape.jobTitle.season";
    static final String SEASON_REFETCH_TITLE = "autoScrape.jobTitle.seasonRefetch";

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
     * @throws AutoScrapeNotPossibleException if the source contains neither movies nor TV shows
     */
    public JobSnapshot start(long sourceId) {
        MediaSource source = sourceService.require(sourceId);
        LocalizedText title = singleTitle(source.getName());
        return switch (source.getMediaType()) {
            case MOVIE -> jobService.submit(JOB_KEY_PREFIX + sourceId, title, context -> run(sourceId, context));
            case TV_SHOW -> jobService.submit(JOB_KEY_PREFIX + sourceId, title,
                    context -> tvRunner.run(sourceId, context));
            default -> throw new AutoScrapeNotPossibleException(source);
        };
    }

    /**
     * Scrapes a single movie.
     *
     * @throws de.tiktak.kmm.movie.MovieNotFoundException if the movie does not exist
     */
    public JobSnapshot startMovie(long movieId) {
        String title = metadataService.findDetails(movieId).metadata().title();
        return jobService.submit(MOVIE_JOB_KEY_PREFIX + movieId, singleTitle(title),
                context -> scrapeMovies(List.of(movieId), context));
    }

    /** Scrapes the selected movies one after the other. */
    public JobSnapshot startMovies(List<Long> movieIds) {
        List<Long> distinct = List.copyOf(new LinkedHashSet<>(movieIds));
        return jobService.submit(MOVIES_JOB_KEY, LocalizedText.of(MOVIES_TITLE).with("count", distinct.size()),
                context -> scrapeMovies(distinct, context));
    }

    /**
     * Scrapes a single TV show.
     *
     * @throws org.springframework.web.ErrorResponseException if the TV show does not exist
     */
    public JobSnapshot startTvShow(long showId) {
        return jobService.submit(TV_SHOW_JOB_KEY_PREFIX + showId, singleTitle(tvRunner.title(showId)),
                context -> tvRunner.run(List.of(showId), context));
    }

    /**
     * Scrapes a season of a TV show. It shares the job key with the TV show so both do not run at the same time.
     *
     * @param overwrite whether titles and texts of the episodes are replaced by those of the provider
     * @throws AutoScrapeNotPossibleException if the season has no episodes
     */
    public JobSnapshot startTvSeason(long showId, int season, boolean overwrite) {
        String title = tvRunner.requireSeason(showId, season);
        return jobService.submit(TV_SHOW_JOB_KEY_PREFIX + showId,
                LocalizedText.of(overwrite ? SEASON_REFETCH_TITLE : SEASON_TITLE).with("season", season)
                        .with("title", title),
                context -> tvRunner.runSeason(showId, season, overwrite, context));
    }

    /** Scrapes the selected TV shows one after the other. */
    public JobSnapshot startTvShows(List<Long> showIds) {
        List<Long> distinct = List.copyOf(new LinkedHashSet<>(showIds));
        return jobService.submit(TV_SHOWS_JOB_KEY, LocalizedText.of(TV_SHOWS_TITLE).with("count", distinct.size()),
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

    /** An entry of the selection may have been removed in the meantime; that does not stop the others. */
    private Optional<String> findTitle(long movieId, AutoScrapeProgress progress) {
        try {
            return Optional.of(metadataService.findDetails(movieId).metadata().title());
        } catch (ErrorResponseException e) {
            progress.failed(AutoScrapeTexts.unknownMovie(movieId), LocalizedTexts.of(e));
            return Optional.empty();
        }
    }

    private void scrapeMovie(long movieId, String title, AutoScrapeProgress progress) {
        try {
            switch (scrapeService.scrapeMissingMetadata(movieId)) {
                case SCRAPED -> {
                    progress.scraped();
                    streamDetails.probeMovieAfterScrape(movieId)
                            .ifPresent(failure -> progress.failed(title, AutoScrapeTexts.technicalDetails(failure)));
                }
                case NO_MATCH -> progress.unmatched(movieId, title);
                case NO_SCRAPER -> progress.warn(AutoScrapeTexts.NO_METADATA_SCRAPER);
                case ALREADY_IDENTIFIED -> {
                    // Maintained metadata stays unchanged.
                }
            }
            ArtworkFillResult artwork = artworkService.fillMissing(movieId);
            progress.imagesSaved(artwork.saved());
            artwork.warnings().forEach(warning -> progress.warn(LocalizedTexts.asText(warning)));
            if (trailerService.fillMissing(movieId)) {
                progress.trailerSaved();
            }
            fillTheme(movieId, title, progress);
            enrich(movieId, title, progress);
            renameService.renameIfEnabled(movieId, RenameTrigger.SCRAPE)
                    .filter(plan -> plan.status() == MovieRenamePlan.Status.CONFLICT
                            || plan.status() == MovieRenamePlan.Status.FAILED)
                    .ifPresent(plan -> progress.warn(AutoScrapeTexts.notRenamed(title, plan.message())));
        } catch (ErrorResponseException e) {
            progress.failed(title, LocalizedTexts.of(e));
        } catch (RuntimeException e) {
            log.warn("Auto-scraping movie {} failed", movieId, e);
            progress.failed(title, AutoScrapeTexts.UNEXPECTED_ERROR);
        }
    }

    /** Missing ratings and empty fields (e.g. Trakt, OMDb); an error there does not stop the rest. */
    private void enrich(long movieId, String title, AutoScrapeProgress progress) {
        try {
            if (enrichmentService.fillMissing(movieId)) {
                progress.enriched();
            }
        } catch (ErrorResponseException e) {
            progress.failed(title, AutoScrapeTexts.enrichment(e));
        }
    }

    /** Missing theme music with a certain match; an error there does not stop the rest. */
    private void fillTheme(long movieId, String title, AutoScrapeProgress progress) {
        try {
            if (themeService.fillMovie(movieId)) {
                progress.themeSaved();
            }
        } catch (ErrorResponseException e) {
            progress.failed(title, AutoScrapeTexts.theme(e));
        }
    }

    private static LocalizedText singleTitle(String title) {
        return LocalizedText.of(SINGLE_TITLE).with("title", title);
    }
}
