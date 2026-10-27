package de.tiktak.kmm.autoscrape;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.web.ErrorResponseException;

import de.tiktak.kmm.artwork.ArtworkFillResult;
import de.tiktak.kmm.artwork.TvShowArtworkService;
import de.tiktak.kmm.job.JobContext;
import de.tiktak.kmm.mediainfo.StreamDetailsService;
import de.tiktak.kmm.tvshow.TvShowAutoScrapeOutcome;
import de.tiktak.kmm.tvshow.TvShowMetadataService;
import de.tiktak.kmm.tvshow.TvShowQueryService;
import de.tiktak.kmm.tvshow.TvShowScrapeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Scrapt alle Serien einer Quelle nacheinander: Zuordnung (nur bei sicherem Treffer), leere
 * Episodenfelder und fehlende Bilder. Vorhandene Daten und Bilder bleiben unverändert.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class TvAutoScrapeRunner {

    static final int MAX_LISTED_ENTRIES = 100;
    static final String NO_TV_SHOW_SCRAPER = "Kein Serien-Scraper ist eingerichtet; es werden nur Bilder geholt.";
    private static final String UNEXPECTED_ERROR = "unerwarteter Fehler";

    private final TvShowQueryService queryService;
    private final TvShowMetadataService metadataService;
    private final TvShowScrapeService scrapeService;
    private final TvShowArtworkService artworkService;
    private final StreamDetailsService streamDetails;

    TvAutoScrapeResult run(long sourceId, JobContext context) {
        List<Long> showIds = queryService.findShowIds(sourceId);
        Progress progress = new Progress();
        for (int index = 0; index < showIds.size(); index++) {
            context.throwIfCancellationRequested();
            long showId = showIds.get(index);
            String title = metadataService.findShow(showId).metadata().title();
            context.reportProgress(index, showIds.size(), title);
            scrapeShow(showId, title, progress);
        }
        context.reportProgress(showIds.size(), showIds.size(), null);
        TvAutoScrapeResult result = progress.toResult(showIds.size());
        log.info("Auto-scraped TV source {}: {} shows, {} scraped, {} unmatched, {} episodes, {} images, {} failures",
                sourceId, result.shows(), result.scraped(), result.unmatched(), result.episodesUpdated(),
                result.imagesSaved(), result.failures().size());
        return result;
    }

    private void scrapeShow(long showId, String title, Progress progress) {
        try {
            TvShowAutoScrapeOutcome outcome = scrapeService.scrapeMissing(showId);
            switch (outcome.status()) {
                case SCRAPED -> progress.scraped++;
                case NO_MATCH -> progress.unmatched(title);
                case NO_SCRAPER -> progress.warn(NO_TV_SHOW_SCRAPER);
                case ALREADY_IDENTIFIED -> {
                    // Gepflegte Metadaten der Serie bleiben unverändert.
                }
            }
            progress.episodesUpdated += outcome.episodesUpdated();
            outcome.failedFiles().forEach(file -> progress.failed(title, "NFO nicht geschrieben: " + file));
            if (outcome.status() == TvShowAutoScrapeOutcome.Status.SCRAPED || outcome.episodesUpdated() > 0) {
                streamDetails.probeEpisodesAfterScrape(showId).forEach(
                        failure -> progress.failed(title, AutoScrapeService.TECHNICAL_DETAILS + failure));
            }
            ArtworkFillResult artwork = artworkService.fillMissing(showId);
            progress.imagesSaved += artwork.saved();
            artwork.warnings().forEach(progress::warn);
        } catch (ErrorResponseException e) {
            progress.failed(title, e.getBody().getDetail());
        } catch (RuntimeException e) {
            log.warn("Auto-scraping TV show {} failed", showId, e);
            progress.failed(title, UNEXPECTED_ERROR);
        }
    }

    /** Sammelt das Ergebnis; Listen werden gekürzt, damit der Job-Zustand klein bleibt. */
    private static final class Progress {

        private int scraped;
        private int unmatched;
        private int episodesUpdated;
        private int imagesSaved;
        private final List<String> unmatchedTitles = new ArrayList<>();
        private final List<String> failures = new ArrayList<>();
        private final Set<String> warnings = new LinkedHashSet<>();

        void unmatched(String title) {
            unmatched++;
            addLimited(unmatchedTitles, title);
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
            return new TvAutoScrapeResult(shows, scraped, unmatched, episodesUpdated, imagesSaved, unmatchedTitles,
                    failures, List.copyOf(warnings));
        }

        private static void addLimited(List<String> entries, String entry) {
            if (entries.size() < MAX_LISTED_ENTRIES) {
                entries.add(entry);
            }
        }
    }
}
