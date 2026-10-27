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
import de.tiktak.kmm.tvshow.TvShowAutoScrapeOutcome;
import de.tiktak.kmm.tvshow.TvShowAutoScrapeOutcome.Status;
import de.tiktak.kmm.tvshow.TvShowDetails;
import de.tiktak.kmm.tvshow.TvShowMetadata;
import de.tiktak.kmm.tvshow.TvShowMetadataService;
import de.tiktak.kmm.tvshow.TvShowQueryService;
import de.tiktak.kmm.tvshow.TvShowScrapeService;

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
    private JobContext context;

    @Mock
    private StreamDetailsService streamDetails;

    @InjectMocks
    private TvAutoScrapeRunner runner;

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
        when(streamDetails.probeEpisodesAfterScrape(2L)).thenReturn(List.of("ffprobe fehlt"));

        TvAutoScrapeResult result = runner.run(SOURCE_ID, context);

        assertThat(result.shows()).isEqualTo(4);
        assertThat(result.scraped()).isZero();
        assertThat(result.episodesUpdated()).isEqualTo(4);
        assertThat(result.imagesSaved()).isEqualTo(3);
        assertThat(result.warnings()).containsExactly(TvAutoScrapeRunner.NO_TV_SHOW_SCRAPER, "POSTER: 404");
        assertThat(result.failures()).contains("Lost: Technische Angaben: ffprobe fehlt");
        verify(streamDetails, never()).probeEpisodesAfterScrape(1L);
        assertThat(result.failures()).hasSize(4)
                .first().asString().isEqualTo("Lost: NFO nicht geschrieben: /lost/x.nfo");
        assertThat(result.failures().get(3)).isEqualTo("Defekt: unerwarteter Fehler");
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
