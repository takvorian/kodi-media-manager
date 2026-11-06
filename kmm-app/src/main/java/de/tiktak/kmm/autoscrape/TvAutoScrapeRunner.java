package de.tiktak.kmm.autoscrape;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.web.ErrorResponseException;

import de.tiktak.kmm.artwork.ArtworkFillResult;
import de.tiktak.kmm.artwork.TvShowArtworkService;
import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.job.JobCancelledException;
import de.tiktak.kmm.job.JobContext;
import de.tiktak.kmm.mediainfo.StreamDetailsService;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.source.ScanEntry;
import de.tiktak.kmm.tvshow.TvShowAutoScrapeOutcome;
import de.tiktak.kmm.tvshow.TvShowDetails;
import de.tiktak.kmm.tvshow.TvShowMetadataService;
import de.tiktak.kmm.tvshow.TvShowQueryService;
import de.tiktak.kmm.tvshow.TvShowScrapeService;
import de.tiktak.kmm.theme.ThemeService;
import de.tiktak.kmm.tvshow.TvShowEnrichmentService;
import de.tiktak.kmm.tvshow.TvShowTrailerService;
import de.tiktak.kmm.tvshow.TvRenameTrigger;
import de.tiktak.kmm.tvshow.TvShowRenamePlan;
import de.tiktak.kmm.tvshow.TvShowRenameService;
import de.tiktak.kmm.web.LocalizedTexts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Scrapes TV shows one after the other, all of a source or a selection: identification (only with a certain match),
 * empty episode fields, missing images and missing trailer links. Existing data and images stay unchanged.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class TvAutoScrapeRunner {

    static final int MAX_LISTED_ENTRIES = 100;
    private static final int SEASON_STEPS = 4;

    private final TvShowQueryService queryService;
    private final TvShowMetadataService metadataService;
    private final TvShowScrapeService scrapeService;
    private final TvShowArtworkService artworkService;
    private final TvShowTrailerService trailerService;
    private final ThemeService themeService;
    private final TvShowEnrichmentService enrichmentService;
    private final StreamDetailsService streamDetails;
    private final TvShowRenameService renameService;

    TvAutoScrapeResult run(long sourceId, JobContext context) {
        return run(queryService.findShowIds(sourceId), context);
    }

    String title(long showId) {
        return metadataService.findShow(showId).metadata().title();
    }

    /**
     * @return the title of the TV show
     * @throws AutoScrapeNotPossibleException if the season has no episodes
     */
    String requireSeason(long showId, int season) {
        TvShowDetails show = metadataService.findShow(showId);
        boolean hasEpisodes = show.seasons().stream()
                .anyMatch(candidate -> candidate.number() == season && candidate.episodeCount() > 0);
        if (!hasEpisodes) {
            throw AutoScrapeNotPossibleException.seasonWithoutEpisodes(show.metadata().title(), season);
        }
        return show.metadata().title();
    }

    TvAutoScrapeResult run(List<Long> showIds, JobContext context) {
        Progress progress = new Progress();
        for (int index = 0; index < showIds.size(); index++) {
            context.throwIfCancellationRequested();
            long showId = showIds.get(index);
            Optional<String> title = findTitle(showId, progress);
            context.reportProgress(index, showIds.size(), title.orElse(null));
            title.ifPresent(existing -> scrapeShow(showId, existing, progress));
        }
        context.reportProgress(showIds.size(), showIds.size());
        TvAutoScrapeResult result = progress.toResult(showIds.size());
        log.info("Auto-scraped {} TV shows: {} scraped, {} unmatched, {} episodes, {} images, {} trailers, "
                + "{} themes, {} failures", result.shows(), result.scraped(), result.unmatched(),
                result.episodesUpdated(), result.imagesSaved(), result.trailersSaved(), result.themesSaved(),
                result.failures().size());
        return result;
    }

    /**
     * Scrapes a season: its episodes, the images of the season and the stills of its episodes, technical details and
     * renaming. What concerns the whole TV show (trailer, theme music, ratings) is left out.
     *
     * @param overwrite whether titles and texts of the episodes are replaced by those of the provider
     */
    TvAutoScrapeResult runSeason(long showId, int season, boolean overwrite, JobContext context) {
        Progress progress = new Progress();
        String title = title(showId);
        LocalizedText label = AutoScrapeTexts.season(season, title);
        context.reportProgress(0, SEASON_STEPS, label);
        try {
            TvShowAutoScrapeOutcome outcome = overwrite ? scrapeService.refetchSeason(showId, season)
                    : scrapeService.scrapeSeasonMissing(showId, season);
            if (count(outcome, showId, title, progress)) {
                context.throwIfCancellationRequested();
                context.reportProgress(1, SEASON_STEPS, label);
                streamDetails.probeSeasonAfterScrape(showId, season).forEach(
                        failure -> progress.failed(title, AutoScrapeTexts.technicalDetails(failure)));
            }
            context.throwIfCancellationRequested();
            context.reportProgress(2, SEASON_STEPS, label);
            ArtworkFillResult artwork = artworkService.fillMissingSeason(showId, season);
            progress.imagesSaved += artwork.saved();
            artwork.warnings().forEach(progress::warn);
            context.throwIfCancellationRequested();
            context.reportProgress(3, SEASON_STEPS, label);
            renameService.renameSeasonIfEnabled(showId, season, TvRenameTrigger.SCRAPE)
                    .filter(TvAutoScrapeRunner::notRenamed)
                    .ifPresent(plan -> progress.warn(AutoScrapeTexts.notRenamed(title, plan.message())));
        } catch (ErrorResponseException e) {
            progress.failed(title, LocalizedTexts.of(e));
        } catch (JobCancelledException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Auto-scraping season {} of TV show {} failed", season, showId, e);
            progress.failed(title, AutoScrapeTexts.UNEXPECTED_ERROR);
        }
        context.reportProgress(SEASON_STEPS, SEASON_STEPS);
        TvAutoScrapeResult result = progress.toResult(1);
        log.info("Auto-scraped season {} of TV show {}: {} episodes, {} images, {} failures", season, showId,
                result.episodesUpdated(), result.imagesSaved(), result.failures().size());
        return result;
    }

    /** @return whether something changed in the TV show or its episodes */
    private static boolean count(TvShowAutoScrapeOutcome outcome, long showId, String title, Progress progress) {
        switch (outcome.status()) {
            case SCRAPED -> progress.scraped++;
            case NO_MATCH -> progress.unmatched(showId, title);
            case NO_SCRAPER -> progress.warn(AutoScrapeTexts.NO_TV_SHOW_SCRAPER);
            case ALREADY_IDENTIFIED -> {
                // Maintained metadata of the TV show stays unchanged.
            }
        }
        progress.episodesUpdated += outcome.episodesUpdated();
        outcome.failedFiles().forEach(file -> progress.failed(title, AutoScrapeTexts.nfoNotWritten(file)));
        return outcome.status() == TvShowAutoScrapeOutcome.Status.SCRAPED || outcome.episodesUpdated() > 0;
    }

    private static boolean notRenamed(TvShowRenamePlan plan) {
        return plan.status() == TvShowRenamePlan.Status.CONFLICT || plan.status() == TvShowRenamePlan.Status.FAILED;
    }

    /** An entry of the selection may have been removed in the meantime; that does not stop the others. */
    private Optional<String> findTitle(long showId, Progress progress) {
        try {
            return Optional.of(title(showId));
        } catch (ErrorResponseException e) {
            progress.failed(AutoScrapeTexts.unknownShow(showId), LocalizedTexts.of(e));
            return Optional.empty();
        }
    }

    private void scrapeShow(long showId, String title, Progress progress) {
        try {
            TvShowAutoScrapeOutcome outcome = scrapeService.scrapeMissing(showId);
            if (count(outcome, showId, title, progress)) {
                streamDetails.probeEpisodesAfterScrape(showId).forEach(
                        failure -> progress.failed(title, AutoScrapeTexts.technicalDetails(failure)));
            }
            ArtworkFillResult artwork = artworkService.fillMissing(showId);
            progress.imagesSaved += artwork.saved();
            artwork.warnings().forEach(progress::warn);
            if (trailerService.fillMissing(showId)) {
                progress.trailersSaved++;
            }
            fillTheme(showId, title, progress);
            enrich(showId, title, progress);
            renameService.renameIfEnabled(showId, TvRenameTrigger.SCRAPE)
                    .filter(TvAutoScrapeRunner::notRenamed)
                    .ifPresent(plan -> progress.warn(AutoScrapeTexts.notRenamed(title, plan.message())));
        } catch (ErrorResponseException e) {
            progress.failed(title, LocalizedTexts.of(e));
        } catch (RuntimeException e) {
            log.warn("Auto-scraping TV show {} failed", showId, e);
            progress.failed(title, AutoScrapeTexts.UNEXPECTED_ERROR);
        }
    }

    /** Missing ratings and empty fields (e.g. Trakt, OMDb); an error there does not stop the rest. */
    private void enrich(long showId, String title, Progress progress) {
        try {
            if (enrichmentService.fillMissing(showId)) {
                progress.enriched++;
            }
        } catch (ErrorResponseException e) {
            progress.failed(title, AutoScrapeTexts.enrichment(e));
        }
    }

    /** Missing theme music with a certain match; an error there does not stop the rest. */
    private void fillTheme(long showId, String title, Progress progress) {
        try {
            if (themeService.fillShow(showId)) {
                progress.themesSaved++;
            }
        } catch (ErrorResponseException e) {
            progress.failed(title, AutoScrapeTexts.theme(e));
        }
    }

    /** Collects the result; lists are truncated so the job state stays small. */
    private static final class Progress {

        private int scraped;
        private int unmatched;
        private int episodesUpdated;
        private int imagesSaved;
        private int trailersSaved;
        private int themesSaved;
        private int enriched;
        private final List<ScanEntry> unmatchedEntries = new ArrayList<>();
        private final List<LocalizedText> failures = new ArrayList<>();
        private final Set<LocalizedText> warnings = new LinkedHashSet<>();

        void unmatched(long showId, String title) {
            unmatched++;
            addLimited(unmatchedEntries, new ScanEntry(MediaType.TV_SHOW, showId, title));
        }

        void failed(String title, LocalizedText message) {
            failed(LocalizedText.literal(title), message);
        }

        void failed(LocalizedText entry, LocalizedText message) {
            addLimited(failures, AutoScrapeTexts.failure(entry, message));
        }

        void warn(LocalizedText warning) {
            if (warnings.size() < MAX_LISTED_ENTRIES) {
                warnings.add(warning);
            }
        }

        TvAutoScrapeResult toResult(int shows) {
            return new TvAutoScrapeResult(shows, scraped, unmatched, episodesUpdated, imagesSaved, trailersSaved,
                    themesSaved, enriched, unmatchedEntries, failures, List.copyOf(warnings));
        }

        private static <T> void addLimited(List<T> entries, T entry) {
            if (entries.size() < MAX_LISTED_ENTRIES) {
                entries.add(entry);
            }
        }
    }
}
