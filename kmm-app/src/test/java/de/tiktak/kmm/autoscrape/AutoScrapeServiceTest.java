package de.tiktak.kmm.autoscrape;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import de.tiktak.kmm.artwork.ArtworkFillResult;
import de.tiktak.kmm.artwork.ArtworkService;
import de.tiktak.kmm.job.JobCancelledException;
import de.tiktak.kmm.job.JobContext;
import de.tiktak.kmm.job.JobService;
import de.tiktak.kmm.job.JobSnapshot;
import de.tiktak.kmm.mediainfo.StreamDetailsService;
import de.tiktak.kmm.job.JobStatus;
import de.tiktak.kmm.job.JobTask;
import de.tiktak.kmm.movie.MetadataScrapeOutcome;
import de.tiktak.kmm.movie.MovieDetails;
import de.tiktak.kmm.movie.MovieMetadata;
import de.tiktak.kmm.movie.MovieMetadataService;
import de.tiktak.kmm.movie.MovieQueryService;
import de.tiktak.kmm.movie.MovieScrapeService;
import de.tiktak.kmm.movie.MovieSupplement;
import de.tiktak.kmm.nfo.StreamDetails;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.scanner.DiscFormat;
import de.tiktak.kmm.source.MediaSource;
import de.tiktak.kmm.source.MediaSourceService;

@ExtendWith(MockitoExtension.class)
class AutoScrapeServiceTest {

    private static final long SOURCE_ID = 3L;

    @Mock
    private MediaSourceService sourceService;

    @Mock
    private MovieQueryService queryService;

    @Mock
    private MovieMetadataService metadataService;

    @Mock
    private MovieScrapeService scrapeService;

    @Mock
    private ArtworkService artworkService;

    @Mock
    private JobService jobService;

    @Mock
    private TvAutoScrapeRunner tvRunner;

    @Mock
    private StreamDetailsService streamDetails;

    @InjectMocks
    private AutoScrapeService service;

    @Test
    void startsJobKeyedBySourceAndRunsTheScrape() {
        givenSource(MediaType.MOVIE);
        JobSnapshot started = new JobSnapshot(UUID.randomUUID(), "auto-scrape:3", "Scrapen „Filme“", JobStatus.RUNNING,
                0, 0, null, null, Instant.now(), null);
        when(jobService.submit(eq("auto-scrape:3"), eq("Scrapen „Filme“"), any())).thenReturn(started);
        when(queryService.findMovieIds(SOURCE_ID)).thenReturn(List.of());

        assertThat(service.start(SOURCE_ID)).isEqualTo(started);

        ArgumentCaptor<JobTask<?>> task = ArgumentCaptor.forClass(JobTask.class);
        verify(jobService).submit(anyString(), anyString(), task.capture());
        assertThat(task.getValue().run(new RecordingContext(false)))
                .isEqualTo(new AutoScrapeResult(0, 0, 0, 0, List.of(), List.of(), List.of()));
    }

    @Test
    void runsTheTvShowScrapeForTvSources() {
        givenSource(MediaType.TV_SHOW);
        TvAutoScrapeResult result = new TvAutoScrapeResult(1, 1, 0, 3, 2, List.of(), List.of(), List.of());
        RecordingContext context = new RecordingContext(false);
        when(tvRunner.run(SOURCE_ID, context)).thenReturn(result);

        service.start(SOURCE_ID);

        ArgumentCaptor<JobTask<?>> task = ArgumentCaptor.forClass(JobTask.class);
        verify(jobService).submit(eq("auto-scrape:3"), anyString(), task.capture());
        assertThat(task.getValue().run(context)).isEqualTo(result);
    }

    @Test
    void rejectsSourcesWithoutMoviesOrTvShows() {
        givenSource(MediaType.MOVIE_SET);

        assertThatThrownBy(() -> service.start(SOURCE_ID))
                .isInstanceOfSatisfying(AutoScrapeNotPossibleException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT));
        verify(jobService, never()).submit(anyString(), anyString(), any());
    }

    @Test
    void scrapesEveryMovieAndSummarizesTheOutcome() {
        when(queryService.findMovieIds(SOURCE_ID)).thenReturn(List.of(1L, 2L, 3L, 4L));
        givenMovie(1L, "Heat");
        givenMovie(2L, "Unbekannt");
        givenMovie(3L, "Ronin");
        givenMovie(4L, "Kaputt");
        when(scrapeService.scrapeMissingMetadata(1L)).thenReturn(MetadataScrapeOutcome.SCRAPED);
        when(scrapeService.scrapeMissingMetadata(2L)).thenReturn(MetadataScrapeOutcome.NO_MATCH);
        when(scrapeService.scrapeMissingMetadata(3L)).thenReturn(MetadataScrapeOutcome.ALREADY_IDENTIFIED);
        when(scrapeService.scrapeMissingMetadata(4L)).thenThrow(problem("TMDB ist nicht erreichbar"));
        when(artworkService.fillMissing(1L)).thenReturn(new ArtworkFillResult(7, List.of("fanart.tv: Key fehlt")));
        when(artworkService.fillMissing(2L)).thenReturn(new ArtworkFillResult(0, List.of()));
        when(artworkService.fillMissing(3L)).thenReturn(new ArtworkFillResult(2, List.of("fanart.tv: Key fehlt")));
        when(streamDetails.probeMovieAfterScrape(1L)).thenReturn(Optional.of("ffprobe fehlt"));
        RecordingContext context = new RecordingContext(false);

        AutoScrapeResult result = service.run(SOURCE_ID, context);

        assertThat(result).isEqualTo(new AutoScrapeResult(4, 1, 1, 9, List.of("Unbekannt"),
                List.of("Heat: Technische Angaben: ffprobe fehlt", "Kaputt: TMDB ist nicht erreichbar"),
                List.of("fanart.tv: Key fehlt")));
        verify(streamDetails, never()).probeMovieAfterScrape(3L);
        assertThat(context.progress).containsExactly("0/4 Heat", "1/4 Unbekannt", "2/4 Ronin", "3/4 Kaputt",
                "4/4 null");
        verify(artworkService, never()).fillMissing(4L);
    }

    @Test
    void warnsOnceWhenNoMetadataScraperIsAvailableButStillFetchesImages() {
        when(queryService.findMovieIds(SOURCE_ID)).thenReturn(List.of(1L, 2L));
        givenMovie(1L, "Heat");
        givenMovie(2L, "Ronin");
        when(scrapeService.scrapeMissingMetadata(any(Long.class))).thenReturn(MetadataScrapeOutcome.NO_SCRAPER);
        when(artworkService.fillMissing(any(Long.class))).thenReturn(new ArtworkFillResult(1, List.of()));

        AutoScrapeResult result = service.run(SOURCE_ID, new RecordingContext(false));

        assertThat(result.warnings()).containsExactly(AutoScrapeService.NO_METADATA_SCRAPER);
        assertThat(result.imagesSaved()).isEqualTo(2);
    }

    @Test
    void reportsUnexpectedErrorsWithoutAbortingTheRun() {
        when(queryService.findMovieIds(SOURCE_ID)).thenReturn(List.of(1L, 2L));
        givenMovie(1L, "Heat");
        givenMovie(2L, "Ronin");
        when(scrapeService.scrapeMissingMetadata(1L)).thenThrow(new IllegalStateException("Bug"));
        when(scrapeService.scrapeMissingMetadata(2L)).thenReturn(MetadataScrapeOutcome.SCRAPED);
        when(artworkService.fillMissing(2L)).thenReturn(new ArtworkFillResult(0, List.of()));

        AutoScrapeResult result = service.run(SOURCE_ID, new RecordingContext(false));

        assertThat(result.failures()).containsExactly("Heat: unerwarteter Fehler");
        assertThat(result.scraped()).isEqualTo(1);
    }

    @Test
    void stopsWhenCancelled() {
        when(queryService.findMovieIds(SOURCE_ID)).thenReturn(List.of(1L));

        assertThatExceptionOfType(JobCancelledException.class)
                .isThrownBy(() -> service.run(SOURCE_ID, new RecordingContext(true)));
        verify(scrapeService, never()).scrapeMissingMetadata(any(Long.class));
    }

    private void givenSource(MediaType mediaType) {
        MediaSource source = mock(MediaSource.class);
        when(source.getName()).thenReturn("Filme");
        when(source.getMediaType()).thenReturn(mediaType);
        when(sourceService.require(SOURCE_ID)).thenReturn(source);
    }

    private void givenMovie(long movieId, String title) {
        var metadata = new MovieMetadata(title, null, null, null, null, null, null, null, null, null, List.of(), null,
                null, null, null);
        when(metadataService.findDetails(movieId)).thenReturn(new MovieDetails(movieId, SOURCE_ID, "/m/" + title,
                DiscFormat.FILE, 1, null, metadata, MovieSupplement.EMPTY, StreamDetails.EMPTY,
                new MovieDetails.NfoInfo("/m/x.nfo", false, null)));
    }

    private static ErrorResponseException problem(String detail) {
        var exception = new ErrorResponseException(HttpStatus.BAD_GATEWAY);
        exception.getBody().setDetail(detail);
        return exception;
    }

    /** Merkt sich die Fortschrittsmeldungen als „erledigt/gesamt Nachricht“. */
    private static final class RecordingContext implements JobContext {

        private final boolean cancelled;
        private final List<String> progress = new ArrayList<>();

        private RecordingContext(boolean cancelled) {
            this.cancelled = cancelled;
        }

        @Override
        public void reportProgress(int done, int total, String message) {
            progress.add(done + "/" + total + " " + message);
        }

        @Override
        public boolean isCancellationRequested() {
            return cancelled;
        }
    }
}
