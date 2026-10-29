package de.tiktak.kmm.tvshow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.ScrapeRequest;
import de.tiktak.kmm.settings.KmmSettings;
import de.tiktak.kmm.settings.SettingsService;
import de.tiktak.kmm.trailer.TrailerFinder;
import de.tiktak.kmm.trailer.TrailerOption;
import de.tiktak.kmm.trailer.TrailerOptions;

@ExtendWith(MockitoExtension.class)
class TvShowTrailerServiceTest {

    private static final long SHOW_ID = 4L;
    private static final String TRAILER = "plugin://plugin.video.youtube/?action=play_video&videoid=KTu8iDynwNc";

    @Mock
    private TvShowRepository showRepository;

    @Mock
    private TvShowMetadataService metadataService;

    @Mock
    private TvNfoSync nfoSync;

    @Mock
    private TrailerFinder trailerFinder;

    @Mock
    private SettingsService settingsService;

    private TvShowTrailerService service;
    private TvShow show;

    @BeforeEach
    void setUp() {
        service = new TvShowTrailerService(showRepository, metadataService, nfoSync, trailerFinder, settingsService);
        show = new TvShow(1L, "/serien/Lost", "Lost", 2004);
        ReflectionTestUtils.setField(show, "id", SHOW_ID);
        lenient().when(showRepository.findById(SHOW_ID)).thenReturn(Optional.of(show));
        lenient().when(settingsService.current()).thenReturn(KmmSettings.DEFAULTS);
        lenient().when(nfoSync.readTrailerIfUnknown(show)).thenReturn(true);
    }

    @Test
    void searchesTrailersByAllProviderIdsInTheTvArtworkLanguage() {
        givenIds();
        TrailerOptions options = options(TRAILER);
        when(trailerFinder.find(any(), eq(KmmSettings.DEFAULTS.tv().artworkLanguage()))).thenReturn(options);

        assertThat(service.candidates(SHOW_ID)).isEqualTo(options);

        ArgumentCaptor<ScrapeRequest> request = ArgumentCaptor.forClass(ScrapeRequest.class);
        verify(trailerFinder).find(request.capture(), any());
        assertThat(request.getValue()).isEqualTo(new ScrapeRequest(MediaType.TV_SHOW, "Lost", 2004,
                Map.of("tvdb", "73739", "tmdb", "4607", "imdb", "tt0411008")));
    }

    @Test
    void needsAProviderIdToSearchForTrailers() {
        assertThatThrownBy(() -> service.candidates(SHOW_ID))
                .isInstanceOfSatisfying(TvShowProblemException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT));
        assertThatThrownBy(() -> service.candidates(99L))
                .isInstanceOfSatisfying(TvShowProblemException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(trailerFinder);
    }

    @Test
    void appliesAPlayableLinkAndRemovesTheTrailer() {
        TvShowDetails details = mock(TvShowDetails.class);
        when(metadataService.changeTrailer(SHOW_ID, TRAILER)).thenReturn(details);
        when(metadataService.changeTrailer(SHOW_ID, null)).thenReturn(details);

        assertThat(service.apply(SHOW_ID, " " + TRAILER + "  ")).isSameAs(details);
        assertThat(service.remove(SHOW_ID)).isSameAs(details);
    }

    @Test
    void rejectsLinksKodiCannotPlay() {
        for (String invalid : new String[] {"YouTube", "ftp://server/trailer.mp4", " ", null}) {
            assertThatThrownBy(() -> service.apply(SHOW_ID, invalid))
                    .isInstanceOfSatisfying(TvShowProblemException.class,
                            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
        verifyNoInteractions(metadataService);
    }

    @Test
    void fillsAMissingTrailerWithTheBestCandidate() {
        givenIds();
        when(trailerFinder.find(any(), any())).thenReturn(options(TRAILER, "https://example.org/teaser.mp4"));

        assertThat(service.fillMissing(SHOW_ID)).isTrue();

        verify(nfoSync).refreshShowIfChangedOutside(show);
        verify(metadataService).changeTrailer(SHOW_ID, TRAILER);
    }

    @Test
    void keepsAnExistingTrailerWhenFilling() {
        givenIds();
        show.changeTrailer("https://example.org/trailer.mp4");

        assertThat(service.fillMissing(SHOW_ID)).isFalse();

        verifyNoInteractions(trailerFinder, metadataService);
    }

    @Test
    void leavesTheNfoAloneWhenItsTrailerCannotBeRead() {
        givenIds();
        when(nfoSync.readTrailerIfUnknown(show)).thenReturn(false);

        assertThat(service.fillMissing(SHOW_ID)).isFalse();

        verifyNoInteractions(trailerFinder, metadataService);
    }

    @Test
    void skipsShowsWithoutProviderIdOrCandidatesWhenFilling() {
        assertThat(service.fillMissing(SHOW_ID)).isFalse();
        verifyNoInteractions(trailerFinder);

        givenIds();
        when(trailerFinder.find(any(), any())).thenReturn(options());

        assertThat(service.fillMissing(SHOW_ID)).isFalse();
        verify(metadataService, never()).changeTrailer(anyLong(), any());
    }

    private void givenIds() {
        show.applyMetadata(new TvShowMetadata("Lost", null, null, 2004, null, null, null, null, null, List.of(),
                List.of(), List.of(), "73739", "4607", "tt0411008", null));
    }

    private static TrailerOptions options(String... urls) {
        return new TrailerOptions(List.of(urls).stream()
                .map(url -> new TrailerOption("Trailer", url, URI.create("https://www.youtube.com/watch?v=KTu8iDynwNc"),
                        null, "en", "Trailer", true, 480, null, "TMDB"))
                .toList(), List.of());
    }
}
