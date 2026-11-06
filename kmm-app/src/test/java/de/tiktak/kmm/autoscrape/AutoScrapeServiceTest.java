package de.tiktak.kmm.autoscrape;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.i18n.Messages;
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
import de.tiktak.kmm.movie.MovieEnrichmentService;
import de.tiktak.kmm.movie.MovieScrapeService;
import de.tiktak.kmm.movie.RenameTrigger;
import de.tiktak.kmm.movie.MovieRenameService;
import de.tiktak.kmm.movie.MovieRenamePlan;
import de.tiktak.kmm.movie.MovieSupplement;
import de.tiktak.kmm.movie.MovieTrailerService;
import de.tiktak.kmm.nfo.StreamDetails;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.scanner.DiscFormat;
import de.tiktak.kmm.source.MediaSource;
import de.tiktak.kmm.source.MediaSourceService;
import de.tiktak.kmm.source.ScanEntry;
import de.tiktak.kmm.theme.ThemeService;

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

    @Mock
    private MovieRenameService renameService;

    @Mock
    private MovieTrailerService trailerService;

    @Mock
    private ThemeService themeService;

    @Mock
    private MovieEnrichmentService enrichmentService;

    @InjectMocks
    private AutoScrapeService service;

    @Test
    void startsJobKeyedBySourceAndRunsTheScrape() {
        givenSource(MediaType.MOVIE);
        JobSnapshot started = new JobSnapshot(UUID.randomUUID(), "auto-scrape:3", single("Filme"), JobStatus.RUNNING,
                0, 0, null, null, Instant.now(), null);
        when(jobService.submit(eq("auto-scrape:3"), eq(single("Filme")), any())).thenReturn(started);
        when(queryService.findMovieIds(SOURCE_ID)).thenReturn(List.of());

        assertThat(service.start(SOURCE_ID)).isEqualTo(started);

        ArgumentCaptor<JobTask<?>> task = ArgumentCaptor.forClass(JobTask.class);
        verify(jobService).submit(anyString(), any(LocalizedText.class), task.capture());
        assertThat(task.getValue().run(new RecordingContext(false)))
                .isEqualTo(new AutoScrapeResult(0, 0, 0, 0, 0, 0, 0, List.of(), List.of(), List.of()));
    }

    @Test
    void runsTheTvShowScrapeForTvSources() {
        givenSource(MediaType.TV_SHOW);
        TvAutoScrapeResult result = new TvAutoScrapeResult(1, 1, 0, 3, 2, 1, 1, 1, List.of(), List.of(), List.of());
        RecordingContext context = new RecordingContext(false);
        when(tvRunner.run(SOURCE_ID, context)).thenReturn(result);

        service.start(SOURCE_ID);

        ArgumentCaptor<JobTask<?>> task = ArgumentCaptor.forClass(JobTask.class);
        verify(jobService).submit(eq("auto-scrape:3"), any(LocalizedText.class), task.capture());
        assertThat(task.getValue().run(context)).isEqualTo(result);
    }

    @Test
    void scrapesASingleMovieUnderItsOwnKey() {
        givenMovie(1L, "Heat");
        when(scrapeService.scrapeMissingMetadata(1L)).thenReturn(MetadataScrapeOutcome.NO_MATCH);
        when(artworkService.fillMissing(1L)).thenReturn(new ArtworkFillResult(2, List.of()));

        service.startMovie(1L);

        ArgumentCaptor<JobTask<?>> task = ArgumentCaptor.forClass(JobTask.class);
        verify(jobService).submit(eq("movie-scrape:1"), eq(single("Heat")), task.capture());
        assertThat(task.getValue().run(new RecordingContext(false))).isEqualTo(new AutoScrapeResult(1, 0, 1, 2, 0, 0, 0,
                List.of(new ScanEntry(MediaType.MOVIE, 1L, "Heat")), List.of(), List.of()));
    }

    @Test
    void scrapesEachChosenMovieOnceAndNothingElse() {
        givenMovie(1L, "Heat");
        givenMovie(2L, "Ronin");
        when(scrapeService.scrapeMissingMetadata(anyLong())).thenReturn(MetadataScrapeOutcome.ALREADY_IDENTIFIED);
        when(artworkService.fillMissing(anyLong())).thenReturn(new ArtworkFillResult(0, List.of()));

        service.startMovies(List.of(1L, 2L, 1L));

        ArgumentCaptor<JobTask<?>> task = ArgumentCaptor.forClass(JobTask.class);
        verify(jobService).submit(eq("movies-scrape"),
                eq(LocalizedText.of(AutoScrapeService.MOVIES_TITLE).with("count", 2)), task.capture());
        assertThat(task.getValue().run(new RecordingContext(false)))
                .isEqualTo(new AutoScrapeResult(2, 0, 0, 0, 0, 0, 0, List.of(), List.of(), List.of()));
        verify(queryService, never()).findMovieIds(anyLong());
    }

    @Test
    void reportsAChosenMovieThatIsGoneAndScrapesTheOthers() {
        when(metadataService.findDetails(1L)).thenThrow(problem("Film 1 gibt es nicht"));
        givenMovie(2L, "Ronin");
        when(scrapeService.scrapeMissingMetadata(2L)).thenReturn(MetadataScrapeOutcome.ALREADY_IDENTIFIED);
        when(artworkService.fillMissing(2L)).thenReturn(new ArtworkFillResult(1, List.of()));

        service.startMovies(List.of(1L, 2L));

        ArgumentCaptor<JobTask<?>> task = ArgumentCaptor.forClass(JobTask.class);
        verify(jobService).submit(anyString(), any(LocalizedText.class), task.capture());
        assertThat(task.getValue().run(new RecordingContext(false))).isEqualTo(new AutoScrapeResult(2, 0, 0, 1, 0, 0, 0,
                List.of(), List.of(AutoScrapeTexts.failure(AutoScrapeTexts.unknownMovie(1L),
                        LocalizedText.literal("Film 1 gibt es nicht"))), List.of()));
    }

    @Test
    void scrapesASingleTvShowUnderItsOwnKey() {
        TvAutoScrapeResult result = new TvAutoScrapeResult(1, 1, 0, 3, 2, 1, 1, 1, List.of(), List.of(), List.of());
        RecordingContext context = new RecordingContext(false);
        when(tvRunner.title(7L)).thenReturn("Lost");
        when(tvRunner.run(List.of(7L), context)).thenReturn(result);

        service.startTvShow(7L);

        ArgumentCaptor<JobTask<?>> task = ArgumentCaptor.forClass(JobTask.class);
        verify(jobService).submit(eq("tvshow-scrape:7"), eq(single("Lost")), task.capture());
        assertThat(task.getValue().run(context)).isEqualTo(result);
    }

    @Test
    void scrapesASeasonUnderTheKeyOfItsShow() {
        TvAutoScrapeResult result = new TvAutoScrapeResult(1, 0, 0, 2, 1, 0, 0, 0, List.of(), List.of(), List.of());
        RecordingContext context = new RecordingContext(false);
        when(tvRunner.requireSeason(7L, 2)).thenReturn("Lost");
        when(tvRunner.runSeason(7L, 2, true, context)).thenReturn(result);

        service.startTvSeason(7L, 2, true);
        service.startTvSeason(7L, 2, false);

        ArgumentCaptor<JobTask<?>> task = ArgumentCaptor.forClass(JobTask.class);
        verify(jobService).submit(eq("tvshow-scrape:7"), eq(LocalizedText.of(AutoScrapeService.SEASON_REFETCH_TITLE)
                .with("season", 2).with("title", "Lost")), task.capture());
        verify(jobService).submit(eq("tvshow-scrape:7"), eq(LocalizedText.of(AutoScrapeService.SEASON_TITLE)
                .with("season", 2).with("title", "Lost")), any());
        assertThat(task.getValue().run(context)).isEqualTo(result);
    }

    @Test
    void scrapesEachChosenTvShowOnce() {
        TvAutoScrapeResult result = new TvAutoScrapeResult(2, 0, 0, 0, 0, 0, 0, 0, List.of(), List.of(), List.of());
        RecordingContext context = new RecordingContext(false);
        when(tvRunner.run(List.of(7L, 8L), context)).thenReturn(result);

        service.startTvShows(List.of(7L, 8L, 7L));

        ArgumentCaptor<JobTask<?>> task = ArgumentCaptor.forClass(JobTask.class);
        verify(jobService).submit(eq("tvshows-scrape"),
                eq(LocalizedText.of(AutoScrapeService.TV_SHOWS_TITLE).with("count", 2)), task.capture());
        assertThat(task.getValue().run(context)).isEqualTo(result);
    }

    @Test
    void rejectsSourcesWithoutMoviesOrTvShows() {
        givenSource(MediaType.MOVIE_SET);

        assertThatThrownBy(() -> service.start(SOURCE_ID))
                .isInstanceOfSatisfying(AutoScrapeNotPossibleException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT));
        verify(jobService, never()).submit(anyString(), any(LocalizedText.class), any());
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
        when(artworkService.fillMissing(1L)).thenReturn(new ArtworkFillResult(7, List.of(LocalizedText.literal("fanart.tv: Key fehlt"))));
        when(artworkService.fillMissing(2L)).thenReturn(new ArtworkFillResult(0, List.of()));
        when(artworkService.fillMissing(3L)).thenReturn(new ArtworkFillResult(2, List.of(LocalizedText.literal("fanart.tv: Key fehlt"))));
        when(streamDetails.probeMovieAfterScrape(1L)).thenReturn(Optional.of(LocalizedText.literal("ffprobe fehlt")));
        when(trailerService.fillMissing(1L)).thenReturn(true);
        when(themeService.fillMovie(1L)).thenReturn(false);
        when(enrichmentService.fillMissing(1L)).thenReturn(true);
        when(enrichmentService.fillMissing(2L)).thenThrow(problem("NFO gesperrt"));
        when(themeService.fillMovie(2L)).thenReturn(true);
        when(themeService.fillMovie(3L)).thenThrow(problem("TelevisionTunes ist nicht erreichbar"));
        when(renameService.renameIfEnabled(anyLong(), eq(RenameTrigger.SCRAPE))).thenReturn(Optional.empty());
        when(renameService.renameIfEnabled(3L, RenameTrigger.SCRAPE)).thenReturn(Optional.of(new MovieRenamePlan(3,
                "Ronin", "/filme/ronin", "/filme/Ronin", List.of(), MovieRenamePlan.Status.CONFLICT,
                LocalizedText.literal("Der Ordner existiert bereits."))));
        RecordingContext context = new RecordingContext(false);

        AutoScrapeResult result = service.run(SOURCE_ID, context);

        assertThat(result).isEqualTo(new AutoScrapeResult(4, 1, 1, 9, 1, 1, 1,
                List.of(new ScanEntry(MediaType.MOVIE, 2L, "Unbekannt")),
                List.of(failure("Heat", AutoScrapeTexts.technicalDetails(LocalizedText.literal("ffprobe fehlt"))),
                        failure("Unbekannt", LocalizedText.of("autoScrape.enrichment")
                                .with("reason", LocalizedText.literal("NFO gesperrt"))),
                        failure("Ronin", LocalizedText.of("autoScrape.theme")
                                .with("reason", LocalizedText.literal("TelevisionTunes ist nicht erreichbar"))),
                        failure("Kaputt", LocalizedText.literal("TMDB ist nicht erreichbar"))),
                List.of(LocalizedText.literal("fanart.tv: Key fehlt"),
                        AutoScrapeTexts.notRenamed("Ronin", LocalizedText.literal("Der Ordner existiert bereits.")))));
        assertThat(Messages.english(result.failures().getFirst()))
                .isEqualTo("Heat: Technical details: ffprobe fehlt");
        verify(streamDetails, never()).probeMovieAfterScrape(3L);
        verify(renameService, never()).renameIfEnabled(4L, RenameTrigger.SCRAPE);
        assertThat(context.progress).containsExactly("0/4 Heat", "1/4 Unbekannt", "2/4 Ronin", "3/4 Kaputt",
                "4/4 null");
        verify(artworkService, never()).fillMissing(4L);
        verify(trailerService, never()).fillMissing(4L);
        verify(themeService, never()).fillMovie(4L);
        verify(enrichmentService, never()).fillMissing(4L);
    }

    @Test
    void warnsOnceWhenNoMetadataScraperIsAvailableButStillFetchesImages() {
        when(queryService.findMovieIds(SOURCE_ID)).thenReturn(List.of(1L, 2L));
        givenMovie(1L, "Heat");
        givenMovie(2L, "Ronin");
        when(scrapeService.scrapeMissingMetadata(any(Long.class))).thenReturn(MetadataScrapeOutcome.NO_SCRAPER);
        when(artworkService.fillMissing(any(Long.class))).thenReturn(new ArtworkFillResult(1, List.of()));

        AutoScrapeResult result = service.run(SOURCE_ID, new RecordingContext(false));

        assertThat(result.warnings()).containsExactly(AutoScrapeTexts.NO_METADATA_SCRAPER);
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

        assertThat(result.failures()).containsExactly(failure("Heat", AutoScrapeTexts.UNEXPECTED_ERROR));
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

    private static LocalizedText single(String title) {
        return LocalizedText.of(AutoScrapeService.SINGLE_TITLE).with("title", title);
    }

    private static LocalizedText failure(String title, LocalizedText message) {
        return AutoScrapeTexts.failure(LocalizedText.literal(title), message);
    }

    private static ErrorResponseException problem(String detail) {
        var exception = new ErrorResponseException(HttpStatus.BAD_GATEWAY);
        exception.getBody().setDetail(detail);
        return exception;
    }

    /** Records the progress messages as "done/total message". */
    private static final class RecordingContext implements JobContext {

        private final boolean cancelled;
        private final List<String> progress = new ArrayList<>();

        private RecordingContext(boolean cancelled) {
            this.cancelled = cancelled;
        }

        @Override
        public void reportProgress(int done, int total, LocalizedText text) {
            String message = text == null ? null : Messages.english(text);
            progress.add(done + "/" + total + " " + message);
        }

        @Override
        public boolean isCancellationRequested() {
            return cancelled;
        }
    }
}
