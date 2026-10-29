package de.tiktak.kmm.autoscrape;

import java.util.List;

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
import de.tiktak.kmm.movie.MovieRatingService;
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
 * Scrapt alle Filme bzw. Serien einer Quelle im Hintergrund: fehlende Metadaten (nur bei sicherem
 * Treffer), fehlende Bilder und fehlende Trailer-Links. Vorhandene Daten und Bilder bleiben unverändert. Die Einträge werden
 * nacheinander bearbeitet, um die Anfragelimits der Anbieter zu schonen.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoScrapeService {

    /** Präfix des Job-Schlüssels; pro Quelle läuft höchstens ein automatisches Scrapen. */
    public static final String JOB_KEY_PREFIX = "auto-scrape:";
    static final String NO_METADATA_SCRAPER = "Kein Metadaten-Scraper ist eingerichtet; es werden nur Bilder geholt.";
    static final String NOT_RENAMED = "„%s“ nicht umbenannt: %s";
    private static final String UNEXPECTED_ERROR = "unerwarteter Fehler";
    static final String TECHNICAL_DETAILS = "Technische Angaben: ";
    static final String THEME = "Titelmusik: ";
    static final String RATINGS = "Bewertungen: ";

    private final MediaSourceService sourceService;
    private final MovieQueryService queryService;
    private final MovieMetadataService metadataService;
    private final MovieScrapeService scrapeService;
    private final MovieRenameService renameService;
    private final ArtworkService artworkService;
    private final MovieTrailerService trailerService;
    private final ThemeService themeService;
    private final MovieRatingService ratingService;
    private final JobService jobService;
    private final TvAutoScrapeRunner tvRunner;
    private final StreamDetailsService streamDetails;

    /**
     * @throws AutoScrapeNotPossibleException wenn die Quelle weder Filme noch Serien enthält
     */
    public JobSnapshot start(long sourceId) {
        MediaSource source = sourceService.require(sourceId);
        String title = "Scrapen „%s“".formatted(source.getName());
        return switch (source.getMediaType()) {
            case MOVIE -> jobService.submit(JOB_KEY_PREFIX + sourceId, title, context -> run(sourceId, context));
            case TV_SHOW -> jobService.submit(JOB_KEY_PREFIX + sourceId, title,
                    context -> tvRunner.run(sourceId, context));
            default -> throw new AutoScrapeNotPossibleException(source);
        };
    }

    AutoScrapeResult run(long sourceId, JobContext context) {
        List<Long> movieIds = queryService.findMovieIds(sourceId);
        AutoScrapeProgress progress = new AutoScrapeProgress(movieIds.size());
        for (int index = 0; index < movieIds.size(); index++) {
            context.throwIfCancellationRequested();
            long movieId = movieIds.get(index);
            String title = metadataService.findDetails(movieId).metadata().title();
            context.reportProgress(index, movieIds.size(), title);
            scrapeMovie(movieId, title, progress);
        }
        context.reportProgress(movieIds.size(), movieIds.size(), null);
        AutoScrapeResult result = progress.toResult();
        log.info("Auto-scraped source {}: {} movies, {} scraped, {} unmatched, {} images, {} trailers, {} themes, "
                + "{} failures", sourceId, result.movies(), result.scraped(), result.unmatched(), result.imagesSaved(),
                result.trailersSaved(), result.themesSaved(), result.failures().size());
        return result;
    }

    private void scrapeMovie(long movieId, String title, AutoScrapeProgress progress) {
        try {
            switch (scrapeService.scrapeMissingMetadata(movieId)) {
                case SCRAPED -> {
                    progress.scraped();
                    streamDetails.probeMovieAfterScrape(movieId)
                            .ifPresent(failure -> progress.failed(title, TECHNICAL_DETAILS + failure));
                }
                case NO_MATCH -> progress.unmatched(title);
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
            fillRatings(movieId, title, progress);
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

    /** Fehlende Bewertungen weiterer Anbieter; ein Fehler dabei hält den Rest nicht auf. */
    private void fillRatings(long movieId, String title, AutoScrapeProgress progress) {
        try {
            if (ratingService.fillMissing(movieId)) {
                progress.ratingsAdded();
            }
        } catch (ErrorResponseException e) {
            progress.failed(title, RATINGS + e.getBody().getDetail());
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
