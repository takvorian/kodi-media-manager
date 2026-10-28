package de.tiktak.kmm.artwork;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import de.tiktak.kmm.movie.MovieDetails;
import de.tiktak.kmm.movie.MovieFilesChangedEvent;
import de.tiktak.kmm.movie.MovieMetadata;
import de.tiktak.kmm.movie.MovieSupplement;
import de.tiktak.kmm.movie.MovieMetadataService;
import de.tiktak.kmm.nfo.StreamDetails;
import de.tiktak.kmm.plugin.api.ImageType;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.scanner.CompanionFiles;
import de.tiktak.kmm.scanner.DiscFormat;

@ExtendWith(MockitoExtension.class)
class ArtworkServiceTest {

    private static final long MOVIE_ID = 5L;
    private static final Path MOVIE_FILE = Path.of("library", "Heat.mkv").toAbsolutePath();
    private static final CompanionFiles FILES = CompanionFiles.of(MOVIE_FILE, DiscFormat.FILE, 1);
    private static final Instant MODIFIED = Instant.parse("2026-10-25T12:00:00Z");

    @Mock
    private MovieMetadataService metadataService;

    @Mock
    private MovieArtworkFiles artworkFiles;

    @Mock
    private ArtworkCandidateFinder candidateFinder;

    @Mock
    private ImageDownloader downloader;

    @Mock
    private ApplicationEventPublisher events;

    @InjectMocks
    private ArtworkService service;

    @BeforeEach
    void setUp() {
        givenMovie("949", "tt0113277");
    }

    @Test
    void listsAllMovieArtworkTypesWithTheirFiles() {
        Path poster = MOVIE_FILE.resolveSibling("Heat-poster.jpg");
        when(artworkFiles.find(eq(FILES), any())).thenReturn(Optional.empty());
        when(artworkFiles.find(FILES, ImageType.POSTER))
                .thenReturn(Optional.of(new ArtworkFile(ImageType.POSTER, poster, MODIFIED)));

        List<ArtworkInfo> artwork = service.artwork(MOVIE_ID);

        assertThat(artwork).extracting(ArtworkInfo::type).containsExactlyElementsOf(ArtworkTypes.MOVIE_TYPES);
        assertThat(artwork.getFirst()).isEqualTo(new ArtworkInfo(ImageType.POSTER, true, poster.toString(), MODIFIED));
        assertThat(artwork.get(1).exists()).isFalse();
    }

    @Test
    void returnsExistingImageOrReportsItMissing() {
        ArtworkFile poster = new ArtworkFile(ImageType.POSTER, MOVIE_FILE.resolveSibling("Heat-poster.jpg"), MODIFIED);
        when(artworkFiles.find(FILES, ImageType.POSTER)).thenReturn(Optional.of(poster));
        when(artworkFiles.find(FILES, ImageType.BANNER)).thenReturn(Optional.empty());

        assertThat(service.image(MOVIE_ID, ImageType.POSTER)).isEqualTo(poster);
        assertThatThrownBy(() -> service.image(MOVIE_ID, ImageType.BANNER))
                .isInstanceOfSatisfying(ArtworkProblemException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void searchesCandidatesWithKnownIds() {
        var expected = new ArtworkCandidates(List.of(), List.of());
        when(candidateFinder.find(ImageType.POSTER, "Heat", 1995, Map.of("tmdb", "949", "imdb", "tt0113277")))
                .thenReturn(expected);

        assertThat(service.candidates(MOVIE_ID, ImageType.POSTER)).isSameAs(expected);
    }

    @Test
    void requiresAnIdForTheImageSearch() {
        givenMovie(null, null);

        assertThatThrownBy(() -> service.candidates(MOVIE_ID, ImageType.POSTER))
                .isInstanceOfSatisfying(ArtworkProblemException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
                    assertThat(exception.getBody().getDetail()).contains("Online suchen");
                });
    }

    @Test
    void downloadsAndSavesChosenImage() {
        var image = new DownloadedImage(new byte[] {1}, "jpg");
        Path saved = MOVIE_FILE.resolveSibling("Heat-fanart.jpg");
        when(downloader.download(URI.create("https://img.example.org/fanart.jpg"))).thenReturn(image);
        when(artworkFiles.save(FILES, ImageType.FANART, image))
                .thenReturn(new ArtworkFile(ImageType.FANART, saved, MODIFIED));

        ArtworkInfo info = service.apply(MOVIE_ID, ImageType.FANART, " https://img.example.org/fanart.jpg ");

        assertThat(info).isEqualTo(new ArtworkInfo(ImageType.FANART, true, saved.toString(), MODIFIED));
        verify(events).publishEvent(new MovieFilesChangedEvent(MOVIE_ID));
    }

    @Test
    void rejectsMalformedUrlBeforeDownloading() {
        assertThatThrownBy(() -> service.apply(MOVIE_ID, ImageType.FANART, "http://bad url"))
                .isInstanceOfSatisfying(ArtworkProblemException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(downloader, never()).download(any());
    }

    @Test
    void removesImage() {
        service.remove(MOVIE_ID, ImageType.CLEARART);

        verify(artworkFiles).delete(FILES, ImageType.CLEARART);
        verify(events).publishEvent(new MovieFilesChangedEvent(MOVIE_ID));
    }

    @Test
    void rejectsArtworkTypesThatAreNotManagedForMovies() {
        assertThatThrownBy(() -> service.apply(MOVIE_ID, ImageType.CHARACTERART, "https://img/x.jpg"))
                .isInstanceOfSatisfying(ArtworkProblemException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(downloader, never()).download(any());
        verify(candidateFinder, never()).find(any(), anyString(), any(), any());
    }

    @Test
    void fillsOnlyMissingTypesWithTheBestLoadableCandidate() {
        when(artworkFiles.find(eq(FILES), any())).thenReturn(Optional.empty());
        when(artworkFiles.find(FILES, ImageType.POSTER))
                .thenReturn(Optional.of(new ArtworkFile(ImageType.POSTER, MOVIE_FILE, MODIFIED)));
        ArtworkCandidate broken = candidate(ImageType.FANART, "https://img/broken.jpg");
        ArtworkCandidate fanart = candidate(ImageType.FANART, "https://img/fanart.jpg");
        ArtworkCandidate logo = candidate(ImageType.CLEARLOGO, "https://img/logo.png");
        when(candidateFinder.findAll(MediaType.MOVIE, "Heat", 1995, Map.of("tmdb", "949", "imdb", "tt0113277"))).thenReturn(
                new ArtworkSearch(Map.of(ImageType.FANART, List.of(broken, fanart), ImageType.CLEARLOGO, List.of(logo),
                        ImageType.POSTER, List.of(candidate(ImageType.POSTER, "https://img/p.jpg"))),
                        List.of("fanart.tv: Key fehlt")));
        var image = new DownloadedImage(new byte[] {1}, "jpg");
        when(downloader.download(broken.url())).thenThrow(ImageDownloadException.downloadFailed(broken.url(), "HTTP 500", null));
        when(downloader.download(fanart.url())).thenReturn(image);
        when(downloader.download(logo.url())).thenReturn(image);

        ArtworkFillResult result = service.fillMissing(MOVIE_ID);

        assertThat(result.saved()).isEqualTo(2);
        assertThat(result.warnings()).containsExactly("fanart.tv: Key fehlt");
        verify(artworkFiles).save(FILES, ImageType.FANART, image);
        verify(artworkFiles).save(FILES, ImageType.CLEARLOGO, image);
        verify(downloader, never()).download(URI.create("https://img/p.jpg"));
        verify(events).publishEvent(new MovieFilesChangedEvent(MOVIE_ID));
    }

    @Test
    void reportsTypesWhoseCandidatesCannotBeLoaded() {
        when(artworkFiles.find(eq(FILES), any())).thenReturn(Optional.empty());
        List<ArtworkCandidate> broken = List.of(candidate(ImageType.BANNER, "https://img/1.jpg"),
                candidate(ImageType.BANNER, "https://img/2.jpg"), candidate(ImageType.BANNER, "https://img/3.jpg"),
                candidate(ImageType.BANNER, "https://img/4.jpg"));
        when(candidateFinder.findAll(any(), anyString(), any(), any()))
                .thenReturn(new ArtworkSearch(Map.of(ImageType.BANNER, broken), List.of()));
        when(downloader.download(any())).thenThrow(ImageDownloadException.downloadFailed(URI.create("https://img"),
                "HTTP 404", null));

        ArtworkFillResult result = service.fillMissing(MOVIE_ID);

        assertThat(result.saved()).isZero();
        assertThat(result.warnings()).singleElement().asString().startsWith("BANNER: ").contains("HTTP 404");
        verify(downloader, times(3)).download(any());
        verifyNoInteractions(events);
    }

    @Test
    void searchesNothingWhenAllArtworkExistsOrIdsAreMissing() {
        when(artworkFiles.find(eq(FILES), any()))
                .thenAnswer(invocation -> Optional.of(new ArtworkFile(invocation.getArgument(1), MOVIE_FILE, MODIFIED)));

        assertThat(service.fillMissing(MOVIE_ID)).isEqualTo(new ArtworkFillResult(0, List.of()));

        givenMovie(null, null);
        when(artworkFiles.find(eq(FILES), any())).thenReturn(Optional.empty());
        assertThat(service.fillMissing(MOVIE_ID).saved()).isZero();
        verify(candidateFinder, never()).findAll(any(), any(), any(), any());
    }

    private static ArtworkCandidate candidate(ImageType type, String url) {
        return new ArtworkCandidate("tmdb", "TMDB", type, URI.create(url), URI.create(url), null, null, "de", 1.0,
                null);
    }

    private void givenMovie(String tmdbId, String imdbId) {
        var metadata = new MovieMetadata("Heat", null, null, 1995, null, null, null, null, null, null, List.of(),
                imdbId, tmdbId, null, null);
        lenient().when(metadataService.findDetails(MOVIE_ID)).thenReturn(new MovieDetails(
                MOVIE_ID, 1L, MOVIE_FILE.toString(), DiscFormat.FILE, 1, MODIFIED, metadata,
                MovieSupplement.EMPTY, StreamDetails.EMPTY, new MovieDetails.NfoInfo("Heat.nfo", false, null)));
    }
}
