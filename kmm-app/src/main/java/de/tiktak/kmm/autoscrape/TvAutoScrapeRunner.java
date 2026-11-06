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
import de.tiktak.kmm.i18n.Messages;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Scrapt Serien nacheinander, alle einer Quelle oder eine Auswahl: Zuordnung (nur bei sicherem Treffer), leere
 * Episodenfelder, fehlende Bilder und fehlende Trailer-Links. Vorhandene Daten und Bilder bleiben unverändert.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class TvAutoScrapeRunner {

    static final int MAX_LISTED_ENTRIES = 100;
    static final String NO_TV_SHOW_SCRAPER = "Kein Serien-Scraper ist eingerichtet; es werden nur Bilder geholt.";
    static final String UNKNOWN_SHOW = "Serie %d";
    static final String SEASON_LABEL = "Staffel %d von „%s“";
    private static final int SEASON_STEPS = 4;
    private static final String UNEXPECTED_ERROR = "unerwarteter Fehler";

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
     * @return der Titel der Serie
     * @throws AutoScrapeNotPossibleException wenn die Staffel keine Episoden hat
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
     * Scrapt eine Staffel: ihre Episoden, die Bilder der Staffel und die Standbilder ihrer Episoden, technische
     * Angaben und Umbenennen. Was die ganze Serie betrifft (Trailer, Titelmusik, Bewertungen), bleibt weg.
     *
     * @param overwrite ob Titel und Texte der Episoden durch die des Anbieters ersetzt werden
     */
    TvAutoScrapeResult runSeason(long showId, int season, boolean overwrite, JobContext context) {
        Progress progress = new Progress();
        String title = title(showId);
        context.reportProgress(0, SEASON_STEPS, SEASON_LABEL.formatted(season, title));
        try {
            TvShowAutoScrapeOutcome outcome = overwrite ? scrapeService.refetchSeason(showId, season)
                    : scrapeService.scrapeSeasonMissing(showId, season);
            if (count(outcome, showId, title, progress)) {
                context.throwIfCancellationRequested();
                context.reportProgress(1, SEASON_STEPS, SEASON_LABEL.formatted(season, title));
                streamDetails.probeSeasonAfterScrape(showId, season).forEach(failure -> progress.failed(title,
                        AutoScrapeService.TECHNICAL_DETAILS + Messages.english(failure)));
            }
            context.throwIfCancellationRequested();
            context.reportProgress(2, SEASON_STEPS, SEASON_LABEL.formatted(season, title));
            ArtworkFillResult artwork = artworkService.fillMissingSeason(showId, season);
            progress.imagesSaved += artwork.saved();
            artwork.warnings().forEach(warning -> progress.warn(Messages.english(warning)));
            context.throwIfCancellationRequested();
            context.reportProgress(3, SEASON_STEPS, SEASON_LABEL.formatted(season, title));
            renameService.renameSeasonIfEnabled(showId, season, TvRenameTrigger.SCRAPE)
                    .filter(TvAutoScrapeRunner::notRenamed)
                    .ifPresent(plan -> progress.warn(AutoScrapeService.NOT_RENAMED.formatted(title, plan.message())));
        } catch (ErrorResponseException e) {
            progress.failed(title, e.getBody().getDetail());
        } catch (JobCancelledException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Auto-scraping season {} of TV show {} failed", season, showId, e);
            progress.failed(title, UNEXPECTED_ERROR);
        }
        context.reportProgress(SEASON_STEPS, SEASON_STEPS);
        TvAutoScrapeResult result = progress.toResult(1);
        log.info("Auto-scraped season {} of TV show {}: {} episodes, {} images, {} failures", season, showId,
                result.episodesUpdated(), result.imagesSaved(), result.failures().size());
        return result;
    }

    /** @return ob sich an Serie oder Episoden etwas geändert hat */
    private static boolean count(TvShowAutoScrapeOutcome outcome, long showId, String title, Progress progress) {
        switch (outcome.status()) {
            case SCRAPED -> progress.scraped++;
            case NO_MATCH -> progress.unmatched(showId, title);
            case NO_SCRAPER -> progress.warn(NO_TV_SHOW_SCRAPER);
            case ALREADY_IDENTIFIED -> {
                // Gepflegte Metadaten der Serie bleiben unverändert.
            }
        }
        progress.episodesUpdated += outcome.episodesUpdated();
        outcome.failedFiles().forEach(file -> progress.failed(title, "NFO nicht geschrieben: " + file));
        return outcome.status() == TvShowAutoScrapeOutcome.Status.SCRAPED || outcome.episodesUpdated() > 0;
    }

    private static boolean notRenamed(TvShowRenamePlan plan) {
        return plan.status() == TvShowRenamePlan.Status.CONFLICT || plan.status() == TvShowRenamePlan.Status.FAILED;
    }

    /** Ein Eintrag der Auswahl kann inzwischen entfernt worden sein; das hält die übrigen nicht auf. */
    private Optional<String> findTitle(long showId, Progress progress) {
        try {
            return Optional.of(title(showId));
        } catch (ErrorResponseException e) {
            progress.failed(UNKNOWN_SHOW.formatted(showId), e.getBody().getDetail());
            return Optional.empty();
        }
    }

    private void scrapeShow(long showId, String title, Progress progress) {
        try {
            TvShowAutoScrapeOutcome outcome = scrapeService.scrapeMissing(showId);
            if (count(outcome, showId, title, progress)) {
                streamDetails.probeEpisodesAfterScrape(showId).forEach(failure -> progress.failed(title,
                        AutoScrapeService.TECHNICAL_DETAILS + Messages.english(failure)));
            }
            ArtworkFillResult artwork = artworkService.fillMissing(showId);
            progress.imagesSaved += artwork.saved();
            artwork.warnings().forEach(warning -> progress.warn(Messages.english(warning)));
            if (trailerService.fillMissing(showId)) {
                progress.trailersSaved++;
            }
            fillTheme(showId, title, progress);
            enrich(showId, title, progress);
            renameService.renameIfEnabled(showId, TvRenameTrigger.SCRAPE)
                    .filter(TvAutoScrapeRunner::notRenamed)
                    .ifPresent(plan -> progress.warn(AutoScrapeService.NOT_RENAMED.formatted(title, plan.message())));
        } catch (ErrorResponseException e) {
            progress.failed(title, e.getBody().getDetail());
        } catch (RuntimeException e) {
            log.warn("Auto-scraping TV show {} failed", showId, e);
            progress.failed(title, UNEXPECTED_ERROR);
        }
    }

    /** Fehlende Bewertungen und leere Felder (etwa Trakt, OMDb); ein Fehler dabei hält den Rest nicht auf. */
    private void enrich(long showId, String title, Progress progress) {
        try {
            if (enrichmentService.fillMissing(showId)) {
                progress.enriched++;
            }
        } catch (ErrorResponseException e) {
            progress.failed(title, AutoScrapeService.ENRICHMENT + e.getBody().getDetail());
        }
    }

    /** Fehlende Titelmusik bei sicherem Treffer; ein Fehler dabei hält den Rest nicht auf. */
    private void fillTheme(long showId, String title, Progress progress) {
        try {
            if (themeService.fillShow(showId)) {
                progress.themesSaved++;
            }
        } catch (ErrorResponseException e) {
            progress.failed(title, AutoScrapeService.THEME + e.getBody().getDetail());
        }
    }

    /** Sammelt das Ergebnis; Listen werden gekürzt, damit der Job-Zustand klein bleibt. */
    private static final class Progress {

        private int scraped;
        private int unmatched;
        private int episodesUpdated;
        private int imagesSaved;
        private int trailersSaved;
        private int themesSaved;
        private int enriched;
        private final List<ScanEntry> unmatchedEntries = new ArrayList<>();
        private final List<String> failures = new ArrayList<>();
        private final Set<String> warnings = new LinkedHashSet<>();

        void unmatched(long showId, String title) {
            unmatched++;
            addLimited(unmatchedEntries, new ScanEntry(MediaType.TV_SHOW, showId, title));
        }

        void failed(String title, String message) {
            addLimited(failures, "%s: %s".formatted(title, message));
        }

        void warn(String warning) {
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
