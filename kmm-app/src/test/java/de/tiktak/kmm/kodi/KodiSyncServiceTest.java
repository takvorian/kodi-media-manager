package de.tiktak.kmm.kodi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tiktak.kmm.artwork.MovieSetArtworkService;
import de.tiktak.kmm.artwork.MovieSetImage;
import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.i18n.Messages;
import de.tiktak.kmm.kodi.path.KodiPathMapper;
import de.tiktak.kmm.kodi.path.KodiPathMapping;
import de.tiktak.kmm.kodi.rpc.FakeKodiServer;
import de.tiktak.kmm.kodi.rpc.KodiClient;
import de.tiktak.kmm.kodi.rpc.KodiEndpoint;
import de.tiktak.kmm.kodi.rpc.KodiLibraryScheduler;
import de.tiktak.kmm.movie.MovieDetails;
import de.tiktak.kmm.movie.MovieMetadata;
import de.tiktak.kmm.movie.MovieMetadataService;
import de.tiktak.kmm.movie.MovieRenamedEvent;
import de.tiktak.kmm.movie.MovieSetChangedEvent;
import de.tiktak.kmm.movie.MovieSetData;
import de.tiktak.kmm.movie.MovieSetDetails;
import de.tiktak.kmm.movie.MovieSetProblemException;
import de.tiktak.kmm.movie.MovieSetService;
import de.tiktak.kmm.movie.SetSortMethod;
import de.tiktak.kmm.movie.MovieSupplement;
import de.tiktak.kmm.nfo.StreamDetails;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.scanner.DiscFormat;
import de.tiktak.kmm.source.MediaSource;
import de.tiktak.kmm.source.MediaSourceService;
import de.tiktak.kmm.tvshow.EpisodeDetails;
import de.tiktak.kmm.tvshow.TvShowDetails;
import de.tiktak.kmm.tvshow.TvShowMetadata;
import de.tiktak.kmm.tvshow.TvShowMetadataService;
import de.tiktak.kmm.tvshow.TvShowRenamedEvent;
import tools.jackson.databind.JsonNode;

@ExtendWith(MockitoExtension.class)
class KodiSyncServiceTest {

    private static final long MOVIE_ID = 5L;
    private static final Path LIBRARY = Path.of("library").toAbsolutePath();
    private static final Instant NOW = Instant.parse("2026-10-25T20:00:00Z");
    private static final KodiPathMapper FILME = new KodiPathMapper(
            List.of(new KodiPathMapping(LIBRARY.toString(), "smb://nas/filme/")));
    private static final long SHOW_ID = 7L;
    private static final Path SERIES_LIBRARY = Path.of("serien").toAbsolutePath();
    private static final KodiPathMapper SERIEN = new KodiPathMapper(
            List.of(new KodiPathMapping(SERIES_LIBRARY.toString(), "smb://nas/serien/")));

    @Mock
    private KodiHostService hostService;

    @Mock
    private MovieMetadataService metadataService;

    @Mock
    private MovieSetService movieSetService;

    @Mock
    private MovieSetArtworkService movieSetArtwork;

    @Mock
    private MediaSourceService sourceService;

    @Mock
    private TvShowMetadataService tvShowService;

    private final FakeKodiServer kodi = new FakeKodiServer();
    private final KodiSyncLog syncLog = new KodiSyncLog();
    private KodiSyncService service;

    @BeforeEach
    void setUp() {
        KodiClient client = new KodiClient(HttpClient.newHttpClient(), Duration.ofSeconds(5));
        KodiProperties properties = new KodiProperties(Duration.ofSeconds(5), Duration.ofSeconds(5),
                Duration.ofMillis(1), Duration.ZERO, Duration.ofSeconds(5), Duration.ofMillis(5));
        service = new KodiSyncService(hostService, metadataService, movieSetService, movieSetArtwork, tvShowService,
                sourceService, client, new KodiLibraryScheduler(client, properties.scanPollInterval()), properties,
                syncLog, Clock.fixed(NOW, ZoneOffset.UTC));
        kodi.respond("Files.GetSources", """
                {"sources": [{"file": "smb://nas/filme/"}, {"file": "smb://nas/serien/"}]}""");
    }

    @Test
    void warnsWhenKodiHasNoSourceForTheDirectoryItShouldScan() {
        givenHosts(target(1, "Wohnzimmer", kodi.endpoint(), new KodiPathMapper(
                List.of(new KodiPathMapping(LIBRARY.toString(), "a:\\medien\\filme"))), true));
        givenMovie("Sliver/Sliver.mkv", DiscFormat.FILE);
        kodi.respond("VideoLibrary.GetMovies", "{\"limits\": {\"total\": 0}}");

        assertThat(service.refreshMovie(MOVIE_ID)).singleElement().satisfies(result -> {
            assertThat(result.outcome()).isEqualTo(KodiSyncOutcome.NO_KODI_SOURCE);
            assertThat(result.failed()).isTrue();
            assertThat(Messages.english(result.message())).isEqualTo("Kodi has no source for "
                    + "a:\\medien\\filme\\Sliver\\ and will probably find nothing there. Check the path mapping of "
                    + "“Wohnzimmer”. Sources in Kodi: smb://nas/filme/, smb://nas/serien/.");
        });
        assertThat(kodi.methods()).as("Kodi may know the directory anyway").contains("VideoLibrary.Scan");
    }

    @Test
    void scansWithoutWarningWhenTheSourcesCannotBeRead() {
        givenHosts(target(1, "Wohnzimmer", kodi.endpoint(), FILME, true));
        givenMovie("Heat.mkv", DiscFormat.FILE);
        kodi.respond("VideoLibrary.GetMovies", "{\"limits\": {\"total\": 0}}");
        kodi.fail("Files.GetSources", -32601, "Method not found.");

        assertThat(service.refreshMovie(MOVIE_ID)).singleElement().satisfies(
                result -> assertThat(result.outcome()).isEqualTo(KodiSyncOutcome.SCAN_STARTED));
    }

    @AfterEach
    void stopKodi() {
        kodi.close();
    }

    @Test
    void refreshesAMovieKodiKnows() {
        givenHosts(target(1, "Wohnzimmer", kodi.endpoint(), FILME, true));
        givenMovie("Heat (1995)/Heat (1995).mkv", DiscFormat.FILE);
        kodi.respond("VideoLibrary.GetMovies", """
                {"movies": [{"movieid": 7, "file": "smb://nas/filme/Heat (1995)/Heat (1995).mkv"}]}""");

        List<KodiSyncResult> results = service.refreshMovie(MOVIE_ID);

        assertThat(results).containsExactly(
                new KodiSyncResult(1, "Wohnzimmer", KodiSyncOutcome.REFRESHED, refreshed("Heat"), NOW));
        assertThat(kodi.methods()).containsExactly("VideoLibrary.GetMovies", "VideoLibrary.RefreshMovie");
        assertThat(kodi.calls().get(1).params().path("movieid").asInt()).isEqualTo(7);
        assertThat(syncLog.lastResult(1)).contains(results.getFirst());
    }

    @Test
    void letsKodiScanTheMovieFolderOfAnUnknownDvd() {
        givenHosts(target(1, "Wohnzimmer", kodi.endpoint(), FILME, true));
        givenMovie("Heat (1995)/VIDEO_TS/VIDEO_TS.IFO", DiscFormat.DVD);
        kodi.respond("VideoLibrary.GetMovies", "{\"limits\": {\"total\": 0}}");

        List<KodiSyncResult> results = service.refreshMovie(MOVIE_ID);

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.outcome()).isEqualTo(KodiSyncOutcome.SCAN_STARTED);
            assertThat(result.message()).isEqualTo(LocalizedText.of("kodi.sync.scanUnknown")
                    .with("title", "Heat").with("directory", "smb://nas/filme/Heat (1995)/"));
        });
        assertThat(kodi.calls().getFirst().params().path("filter").path("value").asString())
                .isEqualTo("smb://nas/filme/Heat (1995)/VIDEO_TS/");
        assertThat(kodi.calls().getLast().method()).isEqualTo("VideoLibrary.Scan");
        assertThat(kodi.calls().getLast().params().path("directory").asString())
                .isEqualTo("smb://nas/filme/Heat (1995)/");
    }

    @Test
    void reportsThatKodiIsBusyInsteadOfCancellingItsRunningScan() {
        givenHosts(target(1, "Wohnzimmer", kodi.endpoint(), FILME, true));
        givenMovie("Heat.mkv", DiscFormat.FILE);
        kodi.respond("VideoLibrary.GetMovies", "{\"limits\": {\"total\": 0}}");
        kodi.respond("XBMC.GetInfoBooleans", "{\"Library.IsScanningVideo\": true}");

        assertThat(service.refreshMovie(MOVIE_ID)).singleElement().satisfies(result -> {
            assertThat(result.outcome()).isEqualTo(KodiSyncOutcome.FAILED);
            assertThat(result.message().key()).isEqualTo("kodiRpc.libraryBusy");
        });
        assertThat(kodi.methods()).doesNotContain("VideoLibrary.Scan");
    }

    @Test
    void reportsMoviesOutsideTheMappedDirectories() {
        givenHosts(target(1, "Wohnzimmer", kodi.endpoint(), new KodiPathMapper(List.of()), true));
        givenMovie("Heat.mkv", DiscFormat.FILE);

        assertThat(service.refreshMovie(MOVIE_ID)).singleElement().satisfies(result -> {
            assertThat(result.outcome()).isEqualTo(KodiSyncOutcome.NOT_MAPPED);
            assertThat(Messages.english(result.message())).startsWith("No path mapping for ").endsWith("Heat.mkv.");
        });
        assertThat(kodi.calls()).isEmpty();
    }

    @Test
    void continuesWithTheNextHostWhenOneFails() throws IOException {
        int unusedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            unusedPort = socket.getLocalPort();
        }
        givenHosts(target(1, "Aus", new KodiEndpoint("127.0.0.1", unusedPort, null, null), FILME, true),
                target(2, "Wohnzimmer", kodi.endpoint(), FILME, true));
        givenMovie("Heat.mkv", DiscFormat.FILE);

        List<KodiSyncResult> results = service.refreshMovie(MOVIE_ID);

        assertThat(results).extracting(KodiSyncResult::outcome)
                .containsExactly(KodiSyncOutcome.FAILED, KodiSyncOutcome.SCAN_STARTED);
        assertThat(results.getFirst().message().key()).isEqualTo("kodiRpc.unreachable");
        assertThat(syncLog.lastResult(1)).hasValueSatisfying(result -> assertThat(result.failed()).isTrue());
    }

    @Test
    void synchronizesOnlyRealtimeHostsAutomatically() {
        givenHosts(target(1, "Manuell", kodi.endpoint(), FILME, false),
                target(2, "Echtzeit", kodi.endpoint(), FILME, true));
        givenMovie("Heat.mkv", DiscFormat.FILE);

        service.refreshMovieOnRealtimeHosts(MOVIE_ID);

        assertThat(syncLog.lastResult(1)).isEmpty();
        assertThat(syncLog.lastResult(2)).isPresent();
    }

    @Test
    void doesNothingAutomaticallyWithoutRealtimeHosts() {
        givenHosts(target(1, "Manuell", kodi.endpoint(), FILME, false));

        service.refreshMovieOnRealtimeHosts(MOVIE_ID);
        service.scanSourceOnRealtimeHosts(3);

        verifyNoInteractions(metadataService, sourceService);
        assertThat(kodi.calls()).isEmpty();
    }

    @Test
    void letsKodiScanTheNewFolderAndCleanTheOldOneAfterRenaming() {
        givenHosts(target(1, "Wohnzimmer", kodi.endpoint(), FILME, true),
                target(2, "Ohne Zuordnung", kodi.endpoint(), new KodiPathMapper(List.of()), true),
                target(3, "Manuell", kodi.endpoint(), FILME, false));

        service.syncRenamedMovieOnRealtimeHosts(new MovieRenamedEvent(MOVIE_ID, 3,
                LIBRARY.resolve("heat").toString(), LIBRARY.resolve("H").resolve("Heat (1995)").toString()));

        assertThat(kodi.methods()).containsExactly("Files.GetSources", "XBMC.GetInfoBooleans", "VideoLibrary.Scan",
                "XBMC.GetInfoBooleans", "VideoLibrary.Clean");
        assertThat(kodi.calls().get(2).params().path("directory").asString())
                .isEqualTo("smb://nas/filme/H/Heat (1995)/");
        assertThat(kodi.calls().get(4).params().path("directory").asString()).isEqualTo("smb://nas/filme/heat/");
        assertThat(syncLog.lastResult(1)).hasValueSatisfying(result -> assertThat(result.message())
                .isEqualTo(LocalizedText.of("kodi.sync.renamed").with("directory", "smb://nas/filme/H/Heat (1995)/")
                        .with("previous", "smb://nas/filme/heat/")));
        assertThat(syncLog.lastResult(2)).hasValueSatisfying(
                result -> assertThat(result.outcome()).isEqualTo(KodiSyncOutcome.NOT_MAPPED));
        assertThat(syncLog.lastResult(3)).isEmpty();
    }

    @Test
    void letsKodiScanTheNewShowFolderAndCleanTheOldOneAfterRenaming() {
        givenHosts(target(1, "Wohnzimmer", kodi.endpoint(), SERIEN, true));

        service.syncRenamedTvShowOnRealtimeHosts(new TvShowRenamedEvent(SHOW_ID, 2,
                SERIES_LIBRARY.resolve("lost").toString(), SERIES_LIBRARY.resolve("Lost (2004)").toString()));

        assertThat(kodi.methods()).containsExactly("Files.GetSources", "XBMC.GetInfoBooleans", "VideoLibrary.Scan",
                "XBMC.GetInfoBooleans", "VideoLibrary.Clean");
        assertThat(kodi.calls().get(4).params().path("content").asString()).isEqualTo("tvshows");
        assertThat(kodi.calls().get(4).params().path("directory").asString()).endsWith("/lost/");
        service.syncRenamedTvShowOnRealtimeHosts(new TvShowRenamedEvent(SHOW_ID, 2, "/a", "/b"));
        assertThat(syncLog.lastResult(1)).hasValueSatisfying(
                result -> assertThat(result.outcome()).isEqualTo(KodiSyncOutcome.NOT_MAPPED));
    }

    @Test
    void updatesAMovieSetFoundByItsPreviousTitleOrOneOfItsMovies() {
        givenHosts(target(1, "Wohnzimmer", kodi.endpoint(), FILME, true));
        when(movieSetService.find(4)).thenReturn(new MovieSetDetails(4, new MovieSetData("Matrix Trilogie", "Neo",
                null, SetSortMethod.YEAR), List.of(new MovieSetDetails.Member(MOVIE_ID, "Matrix", 1999))));
        givenMovie("Matrix/Matrix.mkv", DiscFormat.FILE);
        kodi.respond("VideoLibrary.GetMovieSets", "{\"sets\": [{\"setid\": 9, \"title\": \"Matrix Filmreihe\"}]}");

        service.syncMovieSetOnRealtimeHosts(new MovieSetChangedEvent(4, "matrix filmreihe"));

        assertThat(kodi.calls().getLast().method()).isEqualTo("VideoLibrary.SetMovieSetDetails");
        assertThat(kodi.calls().getLast().params().path("setid").asInt()).isEqualTo(9);
        assertThat(kodi.calls().getLast().params().path("title").asString()).isEqualTo("Matrix Trilogie");
        assertThat(syncLog.lastResult(1)).hasValueSatisfying(
                result -> assertThat(result.outcome()).isEqualTo(KodiSyncOutcome.UPDATED));

        kodi.respond("VideoLibrary.GetMovieSets", "{\"sets\": []}");
        kodi.respond("VideoLibrary.GetMovies", """
                {"movies": [{"movieid": 12, "file": "smb://nas/filme/Matrix/Matrix.mkv"}]}""");
        kodi.respond("VideoLibrary.GetMovieDetails", "{\"moviedetails\": {\"setid\": 0}}");
        service.syncMovieSetOnRealtimeHosts(new MovieSetChangedEvent(4, "Alt"));
        assertThat(syncLog.lastResult(1)).hasValueSatisfying(
                result -> assertThat(result.outcome()).isEqualTo(KodiSyncOutcome.NOT_IN_KODI));

        when(movieSetService.find(5)).thenThrow(MovieSetProblemException.class);
        service.syncMovieSetOnRealtimeHosts(new MovieSetChangedEvent(5, "Weg"));
    }

    @Test
    void handsTheSetImagesToKodiAndRemovesMissingOnes() {
        givenHosts(target(1, "Wohnzimmer", kodi.endpoint(), FILME, true));
        when(movieSetService.find(4)).thenReturn(new MovieSetDetails(4, new MovieSetData("Matrix", null, null,
                SetSortMethod.YEAR), List.of()));
        when(movieSetArtwork.imagesForKodi(4)).thenReturn(Optional.of(List.of(
                new MovieSetImage("poster", LIBRARY.resolve("_filmsets").resolve("Matrix-poster.jpg")),
                new MovieSetImage("fanart", null),
                new MovieSetImage("banner", Path.of("elsewhere", "Matrix-banner.jpg").toAbsolutePath()))));
        kodi.respond("VideoLibrary.GetMovieSets", "{\"sets\": [{\"setid\": 9, \"title\": \"Matrix\"}]}");
        kodi.respond("Textures.GetTextures", "{\"textures\": [{\"textureid\": 31}]}");

        service.syncMovieSetOnRealtimeHosts(new MovieSetChangedEvent(4, "Matrix"));

        JsonNode art = kodi.calls().get(1).params().path("art");
        assertThat(art.path("poster").asString()).isEqualTo("smb://nas/filme/_filmsets/Matrix-poster.jpg");
        assertThat(art.path("fanart").isNull()).isTrue();
        assertThat(art.has("banner")).as("outside the path mapping").isFalse();
        assertThat(kodi.calls()).extracting(call -> call.method()).containsExactly("VideoLibrary.GetMovieSets",
                "VideoLibrary.SetMovieSetDetails", "Textures.GetTextures", "Textures.RemoveTexture");
    }

    @Test
    void renamingDoesNothingWithoutRealtimeHosts() {
        givenHosts(target(1, "Manuell", kodi.endpoint(), FILME, false));

        service.syncRenamedMovieOnRealtimeHosts(new MovieRenamedEvent(MOVIE_ID, 3, "/a", "/b"));

        assertThat(kodi.calls()).isEmpty();
    }

    @Test
    void letsKodiScanASource() {
        givenHosts(target(1, "Wohnzimmer", kodi.endpoint(), FILME, true),
                target(2, "Ohne Zuordnung", kodi.endpoint(), new KodiPathMapper(List.of()), true));
        when(sourceService.require(3)).thenReturn(new MediaSource("Filme", LIBRARY.toString(), MediaType.MOVIE));

        service.scanSourceOnRealtimeHosts(3);

        assertThat(syncLog.lastResult(1)).hasValueSatisfying(result -> {
            assertThat(result.outcome()).isEqualTo(KodiSyncOutcome.SCAN_STARTED);
            assertThat(Messages.english(result.message())).isEqualTo("Kodi is looking for new movies in smb://nas/filme/.");
        });
        assertThat(syncLog.lastResult(2)).hasValueSatisfying(
                result -> assertThat(result.outcome()).isEqualTo(KodiSyncOutcome.NOT_MAPPED));
        assertThat(kodi.methods()).containsExactly("Files.GetSources", "XBMC.GetInfoBooleans", "VideoLibrary.Scan");
        assertThat(service.scanSource(3)).hasSize(2);
    }

    @Test
    void refreshesATvShowKodiKnowsAndScansUnknownOnes() {
        givenHosts(target(1, "Wohnzimmer", kodi.endpoint(), SERIEN, true));
        givenShow();
        kodi.respond("VideoLibrary.GetTVShows", "{\"tvshows\": [{\"tvshowid\": 4, \"file\": \"smb://nas/serien/Lost/\"}]}");

        assertThat(service.refreshTvShow(SHOW_ID)).singleElement().satisfies(result -> {
            assertThat(result.outcome()).isEqualTo(KodiSyncOutcome.REFRESHED);
            assertThat(result.message()).isEqualTo(refreshed("Lost"));
        });
        assertThat(kodi.methods()).containsExactly("VideoLibrary.GetTVShows", "VideoLibrary.RefreshTVShow");

        kodi.respond("VideoLibrary.GetTVShows", "{\"limits\": {\"total\": 0}}");
        service.refreshTvShowOnRealtimeHosts(SHOW_ID);
        assertThat(syncLog.lastResult(1)).hasValueSatisfying(result -> {
            assertThat(result.outcome()).isEqualTo(KodiSyncOutcome.SCAN_STARTED);
            assertThat(result.message().args()).containsEntry("directory", "smb://nas/serien/Lost/");
        });
    }

    @Test
    void refreshesAnEpisodeKodiKnowsAndScansTheShowFolderOtherwise() {
        givenHosts(target(1, "Wohnzimmer", kodi.endpoint(), SERIEN, true));
        givenShow();
        EpisodeDetails episode = mock(EpisodeDetails.class);
        when(episode.tvShowId()).thenReturn(SHOW_ID);
        when(episode.filePath()).thenReturn(SERIES_LIBRARY.resolve("Lost/S1/Lost S01E02.mkv").toString());
        when(episode.season()).thenReturn(1);
        when(episode.episode()).thenReturn(2);
        when(tvShowService.findEpisode(9)).thenReturn(episode);
        kodi.respond("VideoLibrary.GetEpisodes", """
                {"episodes": [{"episodeid": 12, "file": "smb://nas/serien/Lost/S1/Lost S01E02.mkv",
                               "season": 1, "episode": 2}]}""");

        assertThat(service.refreshEpisode(9)).singleElement().satisfies(result ->
                assertThat(result.message()).isEqualTo(refreshed("Lost S01E02")));
        assertThat(kodi.calls().get(1).params().path("episodeid").asInt()).isEqualTo(12);

        kodi.respond("VideoLibrary.GetEpisodes", "{}");
        service.refreshEpisodeOnRealtimeHosts(9);
        assertThat(kodi.calls().getLast().method()).isEqualTo("VideoLibrary.Scan");
        assertThat(kodi.calls().getLast().params().path("directory").asString()).isEqualTo("smb://nas/serien/Lost/");
    }

    @Test
    void reportsTvShowsOutsideTheMappedDirectoriesAndSkipsManualHostsAutomatically() {
        givenHosts(target(1, "Wohnzimmer", kodi.endpoint(), FILME, false));
        givenShow();

        assertThat(service.refreshTvShow(SHOW_ID)).singleElement().satisfies(result ->
                assertThat(result.outcome()).isEqualTo(KodiSyncOutcome.NOT_MAPPED));
        service.refreshTvShowOnRealtimeHosts(SHOW_ID);
        service.refreshEpisodeOnRealtimeHosts(9);
        assertThat(kodi.methods()).isEmpty();
    }

    @Test
    void namesTvSourcesWhenScanning() {
        givenHosts(target(1, "Wohnzimmer", kodi.endpoint(), SERIEN, true));
        when(sourceService.require(4)).thenReturn(new MediaSource("Serien", SERIES_LIBRARY.toString(), MediaType.TV_SHOW));

        assertThat(service.scanSource(4)).singleElement().satisfies(result -> assertThat(result.message())
                .isEqualTo(LocalizedText.of("kodi.sync.scanSource").with("directory", "smb://nas/serien/")
                        .with("kind", "tvShow")));
    }

    private void givenShow() {
        TvShowDetails show = mock(TvShowDetails.class);
        when(show.directoryPath()).thenReturn(SERIES_LIBRARY.resolve("Lost").toString());
        when(show.metadata()).thenReturn(new TvShowMetadata("Lost", null, null, null, null, null, null, null, null,
                List.of(), List.of(), List.of(), null, null, null, null));
        lenient().when(tvShowService.findShow(SHOW_ID)).thenReturn(show);
    }

    @Test
    void removesAMovieOnRealtimeHostsOnly() {
        givenHosts(target(1, "Wohnzimmer", kodi.endpoint(), FILME, true),
                target(2, "Manuell", kodi.endpoint(), FILME, false));
        kodi.respond("VideoLibrary.GetMovies", """
                {"movies": [{"movieid": 7, "file": "smb://nas/filme/Heat (1995)/Heat (1995).mkv"}]}""");

        List<KodiSyncResult> results = service.removeMovieOnRealtimeHosts("Heat",
                LIBRARY.resolve("Heat (1995)/Heat (1995).mkv").toString());

        assertThat(results).containsExactly(new KodiSyncResult(1, "Wohnzimmer", KodiSyncOutcome.REMOVED,
                LocalizedText.of("kodi.sync.removed").with("title", "Heat"), NOW));
        assertThat(kodi.methods()).containsExactly("VideoLibrary.GetMovies", "VideoLibrary.RemoveMovie");
        assertThat(kodi.calls().get(1).params().path("movieid").asInt()).isEqualTo(7);
    }

    @Test
    void reportsAMovieKodiDoesNotKnowAndAMissingMapping() {
        givenHosts(target(1, "Wohnzimmer", kodi.endpoint(), FILME, true),
                target(2, "Ohne Zuordnung", kodi.endpoint(), new KodiPathMapper(List.of()), true));
        kodi.respond("VideoLibrary.GetMovies", "{\"limits\": {\"total\": 0}}");

        List<KodiSyncResult> results = service.removeMovieOnRealtimeHosts("Heat",
                LIBRARY.resolve("Heat.mkv").toString());

        assertThat(results).extracting(KodiSyncResult::outcome)
                .containsExactly(KodiSyncOutcome.NOT_IN_KODI, KodiSyncOutcome.NOT_MAPPED);
        assertThat(kodi.methods()).doesNotContain("VideoLibrary.RemoveMovie");
    }

    @Test
    void removesATvShowWithItsEpisodes() {
        givenHosts(target(1, "Wohnzimmer", kodi.endpoint(), SERIEN, true));
        kodi.respond("VideoLibrary.GetTVShows", """
                {"tvshows": [{"tvshowid": 3, "file": "smb://nas/serien/ALF/"}]}""");

        List<KodiSyncResult> results = service.removeTvShowOnRealtimeHosts("ALF",
                SERIES_LIBRARY.resolve("ALF").toString());

        assertThat(results).extracting(KodiSyncResult::outcome).containsExactly(KodiSyncOutcome.REMOVED);
        assertThat(kodi.methods()).containsExactly("VideoLibrary.GetTVShows", "VideoLibrary.RemoveTVShow");
        assertThat(kodi.calls().get(1).params().path("tvshowid").asInt()).isEqualTo(3);
    }

    @Test
    void removesAllEpisodesOfAMultiEpisodeFile() {
        givenHosts(target(1, "Wohnzimmer", kodi.endpoint(), SERIEN, true));
        kodi.respond("VideoLibrary.GetEpisodes", """
                {"episodes": [
                  {"episodeid": 11, "file": "smb://nas/serien/ALF/Staffel 1/ALF - S01E01E02.mkv",
                   "season": 1, "episode": 1},
                  {"episodeid": 12, "file": "smb://nas/serien/ALF/Staffel 1/ALF - S01E01E02.mkv",
                   "season": 1, "episode": 2}]}""");
        String file = SERIES_LIBRARY.resolve("ALF/Staffel 1/ALF - S01E01E02.mkv").toString();

        List<KodiSyncResult> results = service.removeEpisodesOnRealtimeHosts("ALF",
                List.of(new KodiEpisodeRef(file, 1, 1), new KodiEpisodeRef(file, 1, 2),
                        new KodiEpisodeRef(file, 1, 3)));

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.outcome()).isEqualTo(KodiSyncOutcome.REMOVED);
            assertThat(result.message()).isEqualTo(LocalizedText.of("kodi.sync.episodesRemoved")
                    .with("title", "ALF").with("count", 2));
        });
        assertThat(kodi.methods()).filteredOn("VideoLibrary.RemoveEpisode"::equals).hasSize(2);
    }

    @Test
    void reportsEpisodesKodiDoesNotKnow() {
        givenHosts(target(1, "Wohnzimmer", kodi.endpoint(), SERIEN, true));
        kodi.respond("VideoLibrary.GetEpisodes", "{\"limits\": {\"total\": 0}}");

        List<KodiSyncResult> results = service.removeEpisodesOnRealtimeHosts("ALF",
                List.of(new KodiEpisodeRef(SERIES_LIBRARY.resolve("ALF/x.mkv").toString(), 1, 1)));

        assertThat(results).extracting(KodiSyncResult::outcome).containsExactly(KodiSyncOutcome.NOT_IN_KODI);
    }

    private void givenHosts(KodiTarget... targets) {
        when(hostService.targets()).thenReturn(List.of(targets));
    }

    private void givenMovie(String relativeFile, DiscFormat format) {
        var metadata = new MovieMetadata("Heat", null, null, 1995, null, null, null, null, null, null, List.of(),
                null, null, null, null);
        when(metadataService.findDetails(MOVIE_ID)).thenReturn(new MovieDetails(MOVIE_ID, 1L,
                LIBRARY.resolve(relativeFile).toString(), format, 1, NOW, metadata, MovieSupplement.EMPTY,
                StreamDetails.EMPTY, new MovieDetails.NfoInfo("Heat.nfo", false, null)));
    }

    private static LocalizedText refreshed(String title) {
        return LocalizedText.of("kodi.sync.refreshed").with("title", title);
    }

    private static KodiTarget target(long id, String name, KodiEndpoint endpoint, KodiPathMapper mapper,
            boolean realtime) {
        return new KodiTarget(id, name, endpoint, mapper, realtime);
    }
}
