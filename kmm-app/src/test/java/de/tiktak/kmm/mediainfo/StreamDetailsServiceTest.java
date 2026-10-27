package de.tiktak.kmm.mediainfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import de.tiktak.kmm.movie.MovieStreamDetailsService;
import de.tiktak.kmm.nfo.StreamDetails;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.scanner.DiscFormat;
import de.tiktak.kmm.source.MediaSource;
import de.tiktak.kmm.source.MediaSourceService;
import de.tiktak.kmm.tvshow.EpisodeMedia;
import de.tiktak.kmm.tvshow.EpisodeStreamDetailsService;

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
        verify(jobService).submit(eq("stream-details-new:7"), anyString(), task.capture());
        Object result = task.getValue().run(mock(JobContext.class));
        assertThat(result).isEqualTo(new StreamDetailsJobResult(SOURCE_ID, 2, 0, 0, List.of()));
        verify(episodes).changeStreamDetails(2L, PROBED);
        verify(episodes).changeStreamDetails(3L, PROBED);
        verify(episodes, never()).changeStreamDetails(1L, PROBED);
    }

    @Test
    void skipsNewFilesWhenDisabledEmptyOrAlreadyRunning() {
        service.startNewFiles(SOURCE_ID, MediaType.MOVIE, List.of());
        when(probes.probeNewFiles()).thenReturn(false);
        service.startNewFiles(SOURCE_ID, MediaType.MOVIE, List.of(1L));
        verify(jobService, never()).submit(anyString(), anyString(), any());

        when(probes.probeNewFiles()).thenReturn(true);
        MediaSource source = givenSource(MediaType.MOVIE);
        JobSnapshot running = new JobSnapshot(UUID.randomUUID(), "stream-details-new:7", "Technische Angaben",
                JobStatus.RUNNING, 0, 0, null, null, Instant.now(), null);
        when(jobService.submit(anyString(), anyString(), any())).thenThrow(new JobAlreadyRunningException(running));

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

        assertThat(service.probeEpisodesAfterScrape(SHOW_ID)).singleElement().asString()
                .startsWith("ffprobe kann nicht gestartet werden");
        verify(episodes, never()).changeStreamDetails(anyLong(), any());
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
