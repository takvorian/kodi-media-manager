package de.tiktak.kmm.mediainfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tiktak.kmm.job.JobAlreadyRunningException;
import de.tiktak.kmm.job.JobContext;
import de.tiktak.kmm.job.JobService;
import de.tiktak.kmm.job.JobSnapshot;
import de.tiktak.kmm.job.JobStatus;
import de.tiktak.kmm.job.JobTask;
import de.tiktak.kmm.media.MediaProbe;
import de.tiktak.kmm.media.FfprobeRunner;
import de.tiktak.kmm.movie.MovieMedia;
import de.tiktak.kmm.movie.MovieStreamDetailsService;
import de.tiktak.kmm.nfo.StreamDetails;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.scanner.DiscFormat;
import de.tiktak.kmm.source.MediaSource;
import de.tiktak.kmm.source.MediaSourceService;
import de.tiktak.kmm.tvshow.EpisodeMedia;
import de.tiktak.kmm.tvshow.EpisodeStreamDetailsService;
import de.tiktak.kmm.i18n.LocalizedText;

@ExtendWith(MockitoExtension.class)
class StreamDetailsServiceTest {

    private static final long SOURCE_ID = 7L;
    private static final long SHOW_ID = 3L;
    private static final StreamDetails PROBED = new StreamDetails(List.of(),
            List.of(new StreamDetails.Audio("aac", null, 2, 128)), List.of());
    private static final Path LOST = Path.of("/serien/Lost/Lost - S01E01E02.mkv");
    private static final Path LOST_3 = Path.of("/serien/Lost/Lost - S01E03.mkv");

    @Mock
    private MediaSourceService sourceService;

    @Mock
    private MovieStreamDetailsService movies;

    @Mock
    private EpisodeStreamDetailsService episodes;

    @Mock
    private MediaProbes probes;

    @Mock
    private JobService jobService;

    @InjectMocks
    private StreamDetailsService service;

    private final MediaProbe probe = mock(MediaProbe.class);

    @BeforeEach
    void fakeFfprobe() {
        lenient().when(probes.current()).thenReturn(probe);
        lenient().when(probe.probe(any(), any())).thenReturn(PROBED);
    }

    @Test
    void readsNewEpisodeFilesOnceAndOnlyWhenEnabled() {
        givenTvSource();
        when(probes.probeNewFiles()).thenReturn(true);
        when(episodes.findBySource(SOURCE_ID)).thenReturn(List.of(episode(1, 1, LOST, false),
                episode(2, 2, LOST, false), episode(3, 3, LOST_3, false), episode(4, 4, LOST_3, true)));

        service.startNewFiles(SOURCE_ID, MediaType.TV_SHOW, List.of(2L, 3L));

        ArgumentCaptor<JobTask<?>> task = ArgumentCaptor.forClass(JobTask.class);
        verify(jobService).submit(eq("stream-details-new:7"), any(LocalizedText.class), task.capture());
        Object result = task.getValue().run(mock(JobContext.class));
        assertThat(result).isEqualTo(new StreamDetailsJobResult(SOURCE_ID, 2, 0, 0, List.of()));
        verify(episodes).changeStreamDetails(2L, PROBED);
        verify(episodes).changeStreamDetails(3L, PROBED);
        verify(episodes, never()).changeStreamDetails(1L, PROBED);
    }

    @Test
    void readsFilesThatArriveWhileTheJobForNewFilesIsRunningInTheSameJob() {
        givenTvSource();
        when(probes.probeNewFiles()).thenReturn(true);
        when(episodes.findBySource(SOURCE_ID)).thenReturn(List.of(episode(1, 1, LOST, false),
                episode(3, 3, LOST_3, false)));

        service.startNewFiles(SOURCE_ID, MediaType.TV_SHOW, List.of(1L));
        service.startNewFiles(SOURCE_ID, MediaType.TV_SHOW, List.of(3L));

        ArgumentCaptor<JobTask<?>> task = ArgumentCaptor.forClass(JobTask.class);
        verify(jobService).submit(eq("stream-details-new:7"), any(LocalizedText.class), task.capture());
        assertThat(task.getValue().run(mock(JobContext.class)))
                .isEqualTo(new StreamDetailsJobResult(SOURCE_ID, 2, 0, 0, List.of()));
        verify(episodes).changeStreamDetails(1L, PROBED);
        verify(episodes).changeStreamDetails(3L, PROBED);

        service.startNewFiles(SOURCE_ID, MediaType.TV_SHOW, List.of(3L));
        verify(jobService, times(2)).submit(eq("stream-details-new:7"), any(LocalizedText.class), any());
    }

    @Test
    void skipsNewFilesWhenDisabledEmptyOrAlreadyRunning() {
        service.startNewFiles(SOURCE_ID, MediaType.MOVIE, List.of());
        when(probes.probeNewFiles()).thenReturn(false);
        service.startNewFiles(SOURCE_ID, MediaType.MOVIE, List.of(1L));
        verify(jobService, never()).submit(anyString(), any(LocalizedText.class), any());

        when(probes.probeNewFiles()).thenReturn(true);
        MediaSource source = givenSource(MediaType.MOVIE);
        JobSnapshot running = new JobSnapshot(UUID.randomUUID(), "stream-details-new:7", LocalizedText.literal("Technische Angaben"),
                JobStatus.RUNNING, 0, 0, null, null, Instant.now(), null);
        when(jobService.submit(anyString(), any(LocalizedText.class), any()))
                .thenThrow(new JobAlreadyRunningException(running));

        service.startNewFiles(source.getId(), MediaType.MOVIE, List.of(1L));
    }

    @Test
    void readsAfterScrapingOnlyWhenEnabledAndOnlyEpisodesWithoutDetails() {
        when(probes.probeOnScrape()).thenReturn(false);
        assertThat(service.probeMovieAfterScrape(1L)).isEmpty();
        assertThat(service.probeEpisodesAfterScrape(SHOW_ID)).isEmpty();
        verify(movies, never()).find(anyLong());

        when(probes.probeOnScrape()).thenReturn(true);
        when(episodes.findByShow(SHOW_ID)).thenReturn(List.of(episode(1, 1, LOST, false),
                episode(2, 2, LOST, false), episode(3, 3, LOST_3, true)));
        when(episodes.find(1L)).thenReturn(episode(1, 1, LOST, false));
        when(probes.current()).thenReturn(new MediaProbe(new FfprobeRunner("/fehlt/ffprobe", Duration.ofSeconds(5))));

        assertThat(service.probeEpisodesAfterScrape(SHOW_ID)).hasSize(1);
        verify(episodes, never()).changeStreamDetails(anyLong(), any());
    }

    @Test
    void countsTheEpisodeFilesReadAfterTheOnlineSearch() {
        when(probes.probeOnScrape()).thenReturn(true);
        when(episodes.findByShow(SHOW_ID)).thenReturn(List.of(episode(1, 1, LOST, false),
                episode(2, 2, LOST, false), episode(3, 3, LOST_3, false)));
        when(episodes.find(1L)).thenReturn(episode(1, 1, LOST, false));
        when(episodes.find(3L)).thenReturn(episode(3, 3, LOST_3, false));
        when(episodes.changeStreamDetails(anyLong(), eq(PROBED))).thenReturn(PROBED);

        assertThat(service.probeShowIfEnabled(SHOW_ID)).contains(new ShowStreamDetailsResult(2, List.of()));
        verify(episodes, never()).changeStreamDetails(2L, PROBED);
    }

    @Test
    void readsAMovieAfterTheOnlineSearchOnlyWhenEnabled() {
        when(probes.probeOnScrape()).thenReturn(false);
        assertThat(service.probeMovieIfEnabled(1L)).isEmpty();

        when(probes.probeOnScrape()).thenReturn(true);
        when(movies.find(1L)).thenReturn(new MovieMedia(1L, "Heat", Path.of("/filme/Heat.mkv"), DiscFormat.FILE,
                false));
        when(movies.changeStreamDetails(1L, PROBED)).thenReturn(PROBED);

        assertThat(service.probeMovieIfEnabled(1L)).contains(PROBED);
    }

    @Test
    void rereadsEveryFileOfTheSeasonOnce() {
        EpisodeMedia otherSeason = new EpisodeMedia(5L, "Lost", 2, 1, Path.of("/serien/Lost/Lost - S02E01.mkv"), false);
        when(episodes.findByShow(SHOW_ID)).thenReturn(List.of(episode(3, 3, LOST_3, true), otherSeason,
                episode(1, 1, LOST, true), episode(2, 2, LOST, true)));
        when(episodes.sourceIdOf(SHOW_ID)).thenReturn(SOURCE_ID);
        JobContext context = mock(JobContext.class);

        service.startSeason(SHOW_ID, 1);

        ArgumentCaptor<JobTask<?>> task = ArgumentCaptor.forClass(JobTask.class);
        verify(jobService).submit(eq("stream-details-season:3"), eq(LocalizedText.of("mediaInfo.season.jobTitle")
                .with("season", 1).with("show", "Lost")),
                task.capture());
        assertThat(task.getValue().run(context)).isEqualTo(new StreamDetailsJobResult(SOURCE_ID, 2, 0, 0, List.of()));
        verify(episodes).changeStreamDetails(1L, PROBED);
        verify(episodes).changeStreamDetails(3L, PROBED);
        verify(episodes, never()).changeStreamDetails(2L, PROBED);
        verify(episodes, never()).changeStreamDetails(5L, PROBED);
        verify(context).reportProgress(eq(2), eq(2), anyString());
    }

    @Test
    void rejectsASeasonWithoutEpisodes() {
        when(episodes.findByShow(SHOW_ID)).thenReturn(List.of(episode(1, 1, LOST, false)));
        when(episodes.showTitleOf(SHOW_ID)).thenReturn("Lost");

        assertThatExceptionOfType(MediaInfoProblemException.class).isThrownBy(() -> service.startSeason(SHOW_ID, 4))
                .satisfies(e -> assertThat(e.localizedText()).isEqualTo(LocalizedText.of(
                        "mediaInfo.seasonWithoutEpisodes.detail").with("show", "Lost").with("season", 4)));
        verify(jobService, never()).submit(anyString(), any(LocalizedText.class), any());
    }

    private EpisodeMedia episode(long id, int number, Path file, boolean hasStreams) {
        return new EpisodeMedia(id, "Lost", 1, number, file, hasStreams);
    }

    private void givenTvSource() {
        givenSource(MediaType.TV_SHOW);
    }

    private MediaSource givenSource(MediaType mediaType) {
        MediaSource source = mock(MediaSource.class);
        lenient().when(source.getId()).thenReturn(SOURCE_ID);
        lenient().when(source.getName()).thenReturn("Quelle");
        lenient().when(source.getMediaType()).thenReturn(mediaType);
        when(sourceService.require(SOURCE_ID)).thenReturn(source);
        return source;
    }
}
