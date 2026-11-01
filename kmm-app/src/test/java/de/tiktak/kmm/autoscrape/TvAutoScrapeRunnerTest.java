package de.tiktak.kmm.autoscrape;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import de.tiktak.kmm.artwork.ArtworkFillResult;
import de.tiktak.kmm.artwork.TvShowArtworkService;
import de.tiktak.kmm.job.JobCancelledException;
import de.tiktak.kmm.job.JobContext;
import de.tiktak.kmm.mediainfo.StreamDetailsService;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.source.ScanEntry;
import de.tiktak.kmm.tvshow.TvShowAutoScrapeOutcome;
import de.tiktak.kmm.tvshow.TvShowAutoScrapeOutcome.Status;
import de.tiktak.kmm.tvshow.TvShowDetails;
import de.tiktak.kmm.tvshow.TvShowMetadata;
import de.tiktak.kmm.tvshow.TvShowMetadataService;
import de.tiktak.kmm.tvshow.TvShowQueryService;
import de.tiktak.kmm.tvshow.TvShowScrapeService;
import de.tiktak.kmm.theme.ThemeService;
import de.tiktak.kmm.tvshow.TvShowEnrichmentService;
import de.tiktak.kmm.tvshow.TvShowTrailerService;
import de.tiktak.kmm.tvshow.TvRenameTrigger;
import de.tiktak.kmm.tvshow.TvShowRenamePlan;
import de.tiktak.kmm.tvshow.TvShowRenameService;

@ExtendWith(MockitoExtension.class)
class TvAutoScrapeRunnerTest {

    private static final long SOURCE_ID = 3L;

    @Mock
    private TvShowQueryService queryService;

    @Mock
    private TvShowMetadataService metadataService;

    @Mock
    private TvShowScrapeService scrapeService;

    @Mock
    private TvShowArtworkService artworkService;

    @Mock
    private TvShowTrailerService trailerService;

    @Mock
    private ThemeService themeService;

    @Mock
    private TvShowEnrichmentService enrichmentService;

    @Mock
    private JobContext context;

    @Mock
    private StreamDetailsService streamDetails;

    @Mock
    private TvShowRenameService renameService;

    @InjectMocks
    private TvAutoScrapeRunner runner;

    @Test
    void scrapesOnlyTheChosenShowsAndReportsOneThatIsGone() {
        when(metadataService.findShow(1L)).thenThrow(new ErrorResponseException(HttpStatus.NOT_FOUND));
        givenShow(2L, "Lost");
        when(scrapeService.scrapeMissing(2L)).thenReturn(new TvShowAutoScrapeOutcome(Status.NO_MATCH, 0, List.of()));
        when(artworkService.fillMissing(2L)).thenReturn(new ArtworkFillResult(0, List.of()));

        TvAutoScrapeResult result = runner.run(List.of(1L, 2L), context);

        assertThat(result.shows()).isEqualTo(2);
        assertThat(result.unmatchedEntries()).containsExactly(new ScanEntry(MediaType.TV_SHOW, 2L, "Lost"));
        assertThat(result.failures()).hasSize(1).first().asString().startsWith("Serie 1: ");
        verify(queryService, never()).findShowIds(anyLong());
    }

    @Test
    void summarizesEveryOutcomeAndKeepsGoingAfterErrors() {
        when(queryService.findShowIds(SOURCE_ID)).thenReturn(List.of(1L, 2L, 3L, 4L));
        givenShow(1L, "ALF");
        givenShow(2L, "Lost");
        givenShow(3L, "Kaputt");
        givenShow(4L, "Defekt");
        when(scrapeService.scrapeMissing(1L)).thenReturn(new TvShowAutoScrapeOutcome(Status.NO_SCRAPER, 0, List.of()));
        when(scrapeService.scrapeMissing(2L))
                .thenReturn(new TvShowAutoScrapeOutcome(Status.ALREADY_IDENTIFIED, 4, List.of("/lost/x.nfo")));
        when(scrapeService.scrapeMissing(3L))
                .thenThrow(new ErrorResponseException(HttpStatus.BAD_GATEWAY));
        when(scrapeService.scrapeMissing(4L)).thenThrow(new IllegalStateException("kaputt"));
        when(artworkService.fillMissing(1L)).thenReturn(new ArtworkFillResult(2, List.of("POSTER: 404")));
        when(artworkService.fillMissing(2L)).thenReturn(new ArtworkFillResult(1, List.of()));
        when(trailerService.fillMissing(1L)).thenReturn(true);
        when(themeService.fillShow(1L)).thenReturn(true);
        when(enrichmentService.fillMissing(1L)).thenReturn(true);
        when(enrichmentService.fillMissing(2L)).thenThrow(new ErrorResponseException(HttpStatus.CONFLICT));
        when(themeService.fillShow(2L)).thenThrow(new ErrorResponseException(HttpStatus.BAD_GATEWAY));
        when(streamDetails.probeEpisodesAfterScrape(2L)).thenReturn(List.of("ffprobe fehlt"));
        when(renameService.renameIfEnabled(1L, TvRenameTrigger.SCRAPE)).thenReturn(Optional.empty());
        when(renameService.renameIfEnabled(2L, TvRenameTrigger.SCRAPE)).thenReturn(Optional.of(new TvShowRenamePlan(2,
                "Lost", "/lost", "/Lost", List.of(), TvShowRenamePlan.Status.CONFLICT, "Der Ordner existiert bereits.")));

        TvAutoScrapeResult result = runner.run(SOURCE_ID, context);

        assertThat(result.shows()).isEqualTo(4);
        assertThat(result.scraped()).isZero();
        assertThat(result.episodesUpdated()).isEqualTo(4);
        assertThat(result.imagesSaved()).isEqualTo(3);
        assertThat(result.trailersSaved()).isEqualTo(1);
        assertThat(result.themesSaved()).isEqualTo(1);
        assertThat(result.enriched()).isEqualTo(1);
        assertThat(result.warnings()).containsExactly(TvAutoScrapeRunner.NO_TV_SHOW_SCRAPER, "POSTER: 404",
                "„Lost“ nicht umbenannt: Der Ordner existiert bereits.");
        verify(renameService, never()).renameIfEnabled(3L, TvRenameTrigger.SCRAPE);
        assertThat(result.failures()).contains("Lost: Technische Angaben: ffprobe fehlt");
        verify(streamDetails, never()).probeEpisodesAfterScrape(1L);
        assertThat(result.failures()).hasSize(6)
                .first().asString().isEqualTo("Lost: NFO nicht geschrieben: /lost/x.nfo");
        assertThat(result.failures()).contains("Lost: Titelmusik: null");
        assertThat(result.failures()).contains("Lost: Ergänzen: null");
        assertThat(result.failures().get(5)).isEqualTo("Defekt: unerwarteter Fehler");
        verify(context).reportProgress(4, 4, null);
    }

    @Test
    void stopsWhenCancelled() {
        when(queryService.findShowIds(SOURCE_ID)).thenReturn(List.of(1L));
        doThrow(new JobCancelledException()).when(context).throwIfCancellationRequested();

        assertThatExceptionOfType(JobCancelledException.class).isThrownBy(() -> runner.run(SOURCE_ID, context));
        verify(scrapeService, never()).scrapeMissing(anyLong());
    }

    private void givenShow(long showId, String title) {
        TvShowDetails details = mock(TvShowDetails.class);
        TvShowMetadata metadata = new TvShowMetadata(title, null, null, null, null, null, null, null, null, List.of(),
                List.of(), List.of(), null, null, null, null);
        when(details.metadata()).thenReturn(metadata);
        when(metadataService.findShow(showId)).thenReturn(details);
    }
}
