package de.tiktak.kmm.artwork;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import de.tiktak.kmm.movie.MovieDetails;
import de.tiktak.kmm.movie.MovieMetadata;
import de.tiktak.kmm.movie.MovieMetadataService;
import de.tiktak.kmm.plugin.api.ImageType;
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
    }

    @Test
    void rejectsArtworkTypesThatAreNotManagedForMovies() {
        assertThatThrownBy(() -> service.apply(MOVIE_ID, ImageType.CHARACTERART, "https://img/x.jpg"))
                .isInstanceOfSatisfying(ArtworkProblemException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(downloader, never()).download(any());
        verify(candidateFinder, never()).find(any(), anyString(), any(), any());
    }

    private void givenMovie(String tmdbId, String imdbId) {
        var metadata = new MovieMetadata("Heat", null, null, 1995, null, null, null, null, null, null, List.of(),
                imdbId, tmdbId);
        lenient().when(metadataService.findDetails(MOVIE_ID)).thenReturn(new MovieDetails(
                MOVIE_ID, 1L, MOVIE_FILE.toString(), DiscFormat.FILE, 1, MODIFIED, metadata,
                new MovieDetails.NfoInfo("Heat.nfo", false, null)));
    }
}
