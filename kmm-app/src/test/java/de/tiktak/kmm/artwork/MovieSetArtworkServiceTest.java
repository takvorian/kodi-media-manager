package de.tiktak.kmm.artwork;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.movie.MovieSetChangedEvent;
import de.tiktak.kmm.movie.MovieSetData;
import de.tiktak.kmm.movie.MovieSetDetails;
import de.tiktak.kmm.movie.MovieSetService;
import de.tiktak.kmm.movie.SetSortMethod;
import de.tiktak.kmm.plugin.api.ImageType;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.settings.KmmSettings;
import de.tiktak.kmm.settings.MovieSetSettings;
import de.tiktak.kmm.settings.SettingsService;

@ExtendWith(MockitoExtension.class)
class MovieSetArtworkServiceTest {

    private static final long SET_ID = 7L;
    private static final String TITLE = "Matrix Filmreihe";
    private static final MovieSetSettings FLAT = new MovieSetSettings(true, "/media/_filmsets", false, null, false);
    private static final Instant MODIFIED = Instant.parse("2026-10-28T12:00:00Z");
    private static final ArtworkFile POSTER = new ArtworkFile(ImageType.POSTER,
            Path.of("/media/_filmsets/Matrix Filmreihe-poster.jpg"), MODIFIED);
    private static final URI URL = URI.create("https://image.tmdb.org/t/p/original/matrix.jpg");
    private static final DownloadedImage IMAGE = new DownloadedImage(new byte[] {1}, "jpg");
    private static final List<LocalizedText> WARNINGS = List.of(LocalizedText.literal("fanart.tv: aus"));

    @Mock
    private MovieSetService movieSetService;

    @Mock
    private SettingsService settingsService;

    @Mock
    private MovieSetArtworkFiles artworkFiles;

    @Mock
    private ArtworkCandidateFinder candidateFinder;

    @Mock
    private ImageDownloader downloader;

    @Mock
    private ApplicationEventPublisher events;

    @InjectMocks
    private MovieSetArtworkService service;

    @Test
    void listsAllMovieSetArtworkTypes() {
        givenSet("2344", FLAT);
        when(artworkFiles.find(eq(FLAT), eq(TITLE), any())).thenReturn(Optional.empty());
        when(artworkFiles.find(FLAT, TITLE, ImageType.POSTER)).thenReturn(Optional.of(POSTER));

        List<ArtworkInfo> artwork = service.artwork(SET_ID);

        assertThat(artwork).extracting(ArtworkInfo::type).containsExactlyElementsOf(ArtworkTypes.MOVIE_SET_TYPES);
        assertThat(artwork.getFirst().exists()).isTrue();
        assertThat(artwork.get(1).exists()).isFalse();
    }

    @Test
    void returnsAnExistingImageOrReportsItMissing() {
        givenSet("2344", FLAT);
        when(artworkFiles.find(FLAT, TITLE, ImageType.POSTER)).thenReturn(Optional.of(POSTER));

        assertThat(service.image(SET_ID, ImageType.POSTER)).isEqualTo(POSTER);
        assertThatThrownBy(() -> service.image(SET_ID, ImageType.BANNER)).isInstanceOf(ArtworkProblemException.class);
        assertThatThrownBy(() -> service.image(SET_ID, ImageType.THUMB)).isInstanceOf(ArtworkProblemException.class);
    }

    @Test
    void searchesCandidatesByTheTmdbCollectionId() {
        givenSet("2344");
        ArtworkCandidate candidate = candidate(ImageType.KEYART);
        when(candidateFinder.findAll(MediaType.MOVIE_SET, TITLE, null, Map.of("tmdb", "2344")))
                .thenReturn(new ArtworkSearch(Map.of(ImageType.KEYART, List.of(candidate)), WARNINGS));

        assertThat(service.candidates(SET_ID, ImageType.KEYART))
                .isEqualTo(new ArtworkCandidates(List.of(candidate), WARNINGS));
    }

    @Test
    void needsTheTmdbCollectionIdForTheSearch() {
        givenSet(null);

        assertThatThrownBy(() -> service.candidates(SET_ID, ImageType.POSTER))
                .isInstanceOfSatisfying(ArtworkProblemException.class,
                        e -> assertThat(e.getBody().getTitle()).isEqualTo("Set not matched"));
    }

    @Test
    void savesTheChosenImageAndTellsKodi() {
        givenSet("2344", FLAT);
        when(downloader.download(URL)).thenReturn(IMAGE);
        when(artworkFiles.save(FLAT, TITLE, ImageType.POSTER, IMAGE)).thenReturn(POSTER);

        ArtworkInfo info = service.apply(SET_ID, ImageType.POSTER, " " + URL + " ");

        assertThat(info).isEqualTo(new ArtworkInfo(ImageType.POSTER, true, POSTER.file().toString(), MODIFIED));
        verify(events).publishEvent(new MovieSetChangedEvent(SET_ID, TITLE));
    }

    @Test
    void refusesToSaveOrRemoveWithoutAStorage() {
        givenSettings(MovieSetSettings.DEFAULTS);

        assertThatThrownBy(() -> service.apply(SET_ID, ImageType.POSTER, URL.toString()))
                .isInstanceOfSatisfying(ArtworkProblemException.class,
                        e -> assertThat(e.getBody().getTitle()).isEqualTo("No storage for set images"));
        assertThatThrownBy(() -> service.remove(SET_ID, ImageType.POSTER)).isInstanceOf(ArtworkProblemException.class);
        verifyNoInteractions(downloader, events);
    }

    @Test
    void removesAnImageFromEveryStorage() {
        givenSet("2344", FLAT);

        service.remove(SET_ID, ImageType.FANART);

        verify(artworkFiles).delete(FLAT, TITLE, ImageType.FANART);
        verify(events).publishEvent(new MovieSetChangedEvent(SET_ID, TITLE));
    }

    @Test
    void fillsOnlyMissingImagesWithTheFirstLoadableCandidate() {
        givenSet("2344", FLAT);
        when(artworkFiles.find(eq(FLAT), eq(TITLE), any())).thenReturn(Optional.of(POSTER));
        when(artworkFiles.find(FLAT, TITLE, ImageType.FANART)).thenReturn(Optional.empty());
        ArtworkCandidate fanart = candidate(ImageType.FANART);
        when(candidateFinder.findAll(MediaType.MOVIE_SET, TITLE, null, Map.of("tmdb", "2344")))
                .thenReturn(new ArtworkSearch(Map.of(ImageType.FANART, List.of(fanart)), List.of()));
        when(downloader.download(fanart.url())).thenReturn(IMAGE);

        assertThat(service.fillMissing(SET_ID)).isEqualTo(new ArtworkFillResult(1, List.of()));
        verify(artworkFiles).save(FLAT, TITLE, ImageType.FANART, IMAGE);
        verify(events).publishEvent(new MovieSetChangedEvent(SET_ID, TITLE));
    }

    @Test
    void fillsNothingWithoutStorageOrCollectionIdOrWhenNothingIsMissing() {
        givenSet(null, FLAT);
        assertThat(service.fillMissing(SET_ID)).isEqualTo(new ArtworkFillResult(0, List.of()));

        givenSet("2344", MovieSetSettings.DEFAULTS);
        assertThat(service.fillMissing(SET_ID)).isEqualTo(new ArtworkFillResult(0, List.of()));

        givenSet("2344", FLAT);
        when(artworkFiles.find(eq(FLAT), eq(TITLE), any())).thenReturn(Optional.of(POSTER));
        assertThat(service.fillMissing(SET_ID)).isEqualTo(new ArtworkFillResult(0, List.of()));

        verifyNoInteractions(candidateFinder, events);
    }

    @Test
    void movesTheImagesOfARenamedSet() {
        givenSet("2344", FLAT);

        service.moveToNewTitle(SET_ID, "Matrix");
        service.moveToNewTitle(SET_ID, TITLE);

        verify(artworkFiles).rename(FLAT, "Matrix", TITLE, ArtworkTypes.MOVIE_SET_TYPES);
        verify(artworkFiles, never()).rename(any(), eq(TITLE), anyString(), any());
    }

    @Test
    void listsTheImagesForKodiOnlyIfAStorageIsEnabled() {
        givenSet("2344", FLAT);
        when(artworkFiles.find(eq(FLAT), eq(TITLE), any())).thenReturn(Optional.empty());
        when(artworkFiles.find(FLAT, TITLE, ImageType.POSTER)).thenReturn(Optional.of(POSTER));

        assertThat(service.imagesForKodi(SET_ID)).hasValueSatisfying(images -> {
            assertThat(images).hasSize(ArtworkTypes.MOVIE_SET_TYPES.size());
            assertThat(images.getFirst()).isEqualTo(new MovieSetImage("poster", POSTER.file()));
            assertThat(images.get(1)).isEqualTo(new MovieSetImage("fanart", null));
        });

        givenSettings(MovieSetSettings.DEFAULTS);
        assertThat(service.imagesForKodi(SET_ID)).isEmpty();
    }

    @Test
    void movesNothingWithoutAStorage() {
        givenSet("2344", MovieSetSettings.DEFAULTS);

        service.moveToNewTitle(SET_ID, "Matrix");

        verify(artworkFiles, never()).rename(any(), anyString(), anyString(), any());
    }

    private void givenSet(String tmdbCollectionId, MovieSetSettings settings) {
        givenSet(tmdbCollectionId);
        givenSettings(settings);
    }

    private void givenSet(String tmdbCollectionId) {
        when(movieSetService.find(SET_ID)).thenReturn(new MovieSetDetails(SET_ID,
                new MovieSetData(TITLE, null, tmdbCollectionId, SetSortMethod.YEAR), List.of()));
    }

    private void givenSettings(MovieSetSettings settings) {
        when(settingsService.current()).thenReturn(KmmSettings.DEFAULTS.withMovieSets(settings));
    }

    private static ArtworkCandidate candidate(ImageType type) {
        return new ArtworkCandidate("tmdb", "TMDB", type, URL, URL, 1000, 1500, null, 5.0, null);
    }
}
