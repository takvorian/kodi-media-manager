package de.tiktak.kmm.kodi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.ServerSocket;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.ErrorResponseException;

import de.tiktak.kmm.job.JobCancelledException;
import de.tiktak.kmm.job.JobContext;
import de.tiktak.kmm.job.JobService;
import de.tiktak.kmm.kodi.rpc.FakeKodiServer;
import de.tiktak.kmm.kodi.path.KodiPathMapper;
import de.tiktak.kmm.kodi.path.KodiPathMapping;
import de.tiktak.kmm.kodi.rpc.KodiClient;
import de.tiktak.kmm.kodi.rpc.KodiEndpoint;
import de.tiktak.kmm.movie.MoviePlayback;
import de.tiktak.kmm.movie.MoviePlaybackService;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.source.MediaSource;
import de.tiktak.kmm.source.MediaSourceService;
import de.tiktak.kmm.tvshow.EpisodePlayback;
import de.tiktak.kmm.tvshow.EpisodePlaybackService;

@ExtendWith(MockitoExtension.class)
class KodiPlaybackServiceTest {

    private static final Path LIBRARY = Path.of("filme").toAbsolutePath();
    private static final KodiPathMapper FILME = new KodiPathMapper(
            List.of(new KodiPathMapping(LIBRARY.toString(), "smb://nas/filme/")));
    private static final Instant PLAYED = Instant.parse("2025-04-03T23:14:16Z");

    @Mock
    private KodiHostService hostService;

    @Mock
    private MediaSourceService sourceService;

    @Mock
    private MoviePlaybackService moviePlaybacks;

    @Mock
    private EpisodePlaybackService episodePlaybacks;

    @Mock
    private JobService jobService;

    private final FakeKodiServer kodi = new FakeKodiServer();
    private KodiPlaybackService service;

    @BeforeEach
    void setUp() {
        KodiClient client = new KodiClient(HttpClient.newHttpClient(), Duration.ofSeconds(5));
        service = new KodiPlaybackService(hostService, sourceService, moviePlaybacks, episodePlaybacks, client,
                jobService, Clock.fixed(PLAYED, ZoneOffset.UTC));
    }

    @AfterEach
    void stopKodi() {
        kodi.close();
    }

    @Test
    void startsTheImportAsJobKeyedBySource() {
        when(sourceService.require(3)).thenReturn(source(MediaType.MOVIE));

        service.startSourceImport(3);

        verify(jobService).submit(eq("kodi-playback:3"), eq("Gesehen-Status „Filme“ aus Kodi"), any());
    }

    @Test
    void asksTheNextKodiWhenTheFirstIsUnreachableAndSkipsUnmappedOnes() throws Exception {
        KodiEndpoint unreachable;
        try (ServerSocket socket = new ServerSocket(0)) {
            unreachable = new KodiEndpoint("127.0.0.1", socket.getLocalPort(), null, null);
        }
        KodiPathMapper other = new KodiPathMapper(List.of(new KodiPathMapping("/anderswo", "smb://x/")));
        when(hostService.targets()).thenReturn(List.of(new KodiTarget(1, "Keller", kodi.endpoint(), other, false),
                new KodiTarget(2, "Aus", unreachable, FILME, false),
                new KodiTarget(3, "Wohnzimmer", kodi.endpoint(), FILME, false)));
        when(moviePlaybacks.find(5)).thenReturn(movie(null, null));
        kodi.respond("VideoLibrary.GetMovies", """
                {"movies": [{"file": "smb://nas/filme/Heat/Heat.mkv", "playcount": 2,
                             "lastplayed": "2025-04-03 23:14:16", "userrating": 10}]}""");

        KodiPlaybackResult result = service.importMovie(5);

        assertThat(result).isEqualTo(new KodiPlaybackResult("Wohnzimmer", KodiPlaybackOutcome.UPDATED,
                "„Heat“ aus Kodi übernommen: 2× gesehen, Bewertung 10.", 2, PLAYED, 10));
        verify(moviePlaybacks).changePlayback(5, 2, PLAYED, 10);
        assertThat(kodi.calls()).singleElement().satisfies(call -> assertThat(
                call.params().path("filter").path("value").asString()).isEqualTo("smb://nas/filme/Heat/"));
    }

    @Test
    void reportsMissingMappingsAndUnreachableKodis() throws Exception {
        when(moviePlaybacks.find(5)).thenReturn(movie(null, null));
        when(hostService.targets()).thenReturn(List.of());
        assertThatThrownBy(() -> service.importMovie(5)).isInstanceOfSatisfying(KodiProblemException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT));

        KodiEndpoint unreachable;
        try (ServerSocket socket = new ServerSocket(0)) {
            unreachable = new KodiEndpoint("127.0.0.1", socket.getLocalPort(), null, null);
        }
        when(hostService.targets()).thenReturn(List.of(new KodiTarget(2, "Aus", unreachable, FILME, false)));
        assertThatThrownBy(() -> service.importMovie(5)).isInstanceOfSatisfying(KodiProblemException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
        verify(moviePlaybacks, never()).changePlayback(anyLong(), any(), any(), any());
    }

    @Test
    void countsTheOutcomesOfAMovieSource() {
        MediaSource source = source(MediaType.MOVIE);
        givenKodi();
        kodi.respond("VideoLibrary.GetMovies", """
                {"movies": [{"file": "smb://nas/filme/Heat/Heat.mkv", "playcount": 2, "lastplayed": ""},
                            {"file": "smb://nas/filme/Ronin/Ronin.mkv", "playcount": 1, "lastplayed": ""},
                            {"file": "smb://nas/filme/Alien/Alien.mkv", "playcount": 0, "lastplayed": ""},
                            {"file": "smb://nas/filme/Delicatessen/Delicatessen.mkv", "playcount": 1,
                             "lastplayed": "2025-04-03 23:14:16"}]}""");
        when(moviePlaybacks.findBySource(3)).thenReturn(List.of(movie(7, "Heat", 1), movie(8, "Ronin", 1),
                movie(9, "Alien", 4), movie(10, "Brazil", null), movie(11, "Delicatessen", null)));
        doThrow(new ErrorResponseException(HttpStatus.UNPROCESSABLE_CONTENT)).when(moviePlaybacks)
                .changePlayback(eq(7L), any(), any(), any());

        KodiPlaybackImportResult result = service.importSource(source, mock(JobContext.class));

        assertThat(result).usingRecursiveComparison().ignoringFields("failures").isEqualTo(
                new KodiPlaybackImportResult(3, "Wohnzimmer", 1, 1, 1, 1, 1, List.of()));
        assertThat(result.failures()).singleElement().asString().startsWith("„Heat“: ");
        verify(moviePlaybacks).changePlayback(11, 1, PLAYED, null);
        verify(moviePlaybacks, never()).changePlayback(eq(9L), any(), any(), any());
    }

    @Test
    void takesOverEpisodesOfATvSourceAndStopsWhenCancelled() {
        MediaSource source = source(MediaType.TV_SHOW);
        givenKodi();
        kodi.respond("VideoLibrary.GetEpisodes", """
                {"episodes": [{"file": "smb://nas/filme/Lost/S01E01.mkv", "season": 1, "episode": 1,
                               "playcount": 1, "lastplayed": ""}]}""");
        when(episodePlaybacks.findBySource(3)).thenReturn(List.of(new EpisodePlayback(4, "Lost",
                LIBRARY.resolve("Lost/S01E01.mkv").toString(), 1, 1, null, null, null)));

        assertThat(service.importSource(source, mock(JobContext.class)).updated()).isEqualTo(1);
        verify(episodePlaybacks).changePlayback(4, 1, null, null);

        JobContext cancelled = mock(JobContext.class);
        doThrow(new JobCancelledException()).when(cancelled).throwIfCancellationRequested();
        assertThatExceptionOfType(JobCancelledException.class)
                .isThrownBy(() -> service.importSource(source, cancelled));
    }

    @Test
    void takesOverASingleEpisode() {
        givenKodi();
        kodi.respond("VideoLibrary.GetEpisodes", """
                {"episodes": [{"file": "smb://nas/filme/Lost/S01E01.mkv", "season": 1, "episode": 1,
                               "playcount": 0, "lastplayed": "", "userrating": 0}]}""");
        when(episodePlaybacks.find(4)).thenReturn(new EpisodePlayback(4, "Lost",
                LIBRARY.resolve("Lost/S01E01.mkv").toString(), 1, 1, 3, PLAYED, 6));

        assertThat(service.importEpisode(4)).satisfies(result -> {
            assertThat(result.outcome()).isEqualTo(KodiPlaybackOutcome.KEPT);
            assertThat(result.message())
                    .isEqualTo("„Lost S01E01“: KMM weiß mehr als Kodi, der Stand bleibt.");
            assertThat(result.playCount()).isEqualTo(3);
            assertThat(result.lastPlayedAt()).isEqualTo(PLAYED);
            assertThat(result.userRating()).isEqualTo(6);
        });
        verify(episodePlaybacks, never()).changePlayback(anyLong(), any(), any(), any());
    }

    private void givenKodi() {
        when(hostService.targets()).thenReturn(List.of(new KodiTarget(3, "Wohnzimmer", kodi.endpoint(), FILME,
                false)));
    }

    private static MediaSource source(MediaType type) {
        MediaSource source = new MediaSource(type == MediaType.MOVIE ? "Filme" : "Serien", LIBRARY.toString(), type);
        ReflectionTestUtils.setField(source, "id", 3L);
        return source;
    }

    private static MoviePlayback movie(Integer playCount, Instant lastPlayedAt) {
        return new MoviePlayback(5, "Heat", LIBRARY.resolve("Heat/Heat.mkv").toString(), playCount, lastPlayedAt,
                null);
    }

    private static MoviePlayback movie(long id, String title, Integer playCount) {
        return new MoviePlayback(id, title, LIBRARY.resolve(title + "/" + title + ".mkv").toString(), playCount, null,
                null);
    }
}
