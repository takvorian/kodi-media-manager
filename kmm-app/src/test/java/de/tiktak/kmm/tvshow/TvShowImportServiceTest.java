package de.tiktak.kmm.tvshow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import de.tiktak.kmm.nfo.StreamDetails;
import de.tiktak.kmm.tvshow.TvShowImportService.ImportedTvShow;
import de.tiktak.kmm.tvshow.TvShowImportService.TvShowImportSession;

@ExtendWith(MockitoExtension.class)
class TvShowImportServiceTest {

    private static final String SHOW_DIRECTORY = Path.of("/serien/Lost").toAbsolutePath().toString();
    private static final String EPISODE_FILE = Path.of(SHOW_DIRECTORY, "Lost S01E01E02.mkv").toString();
    private static final Instant ADDED_AT = Instant.parse("2016-11-29T23:20:15Z");
    private static final Instant NFO_MODIFIED_AT = Instant.parse("2024-01-01T10:00:00Z");
    private static final StreamDetails STREAMS = new StreamDetails(
            List.of(new StreamDetails.Video("h264", 1.78, 1280, 720, null, null, null, null, null)), List.of(),
            List.of());

    @Mock
    private TvShowRepository showRepository;

    @Mock
    private EpisodeRepository episodeRepository;

    @Mock
    private TvShowNfoFiles nfoFiles;

    @InjectMocks
    private TvShowImportService service;

    @BeforeEach
    void savesWithIds() {
        lenient().when(showRepository.save(any())).thenAnswer(invocation -> {
            TvShow show = invocation.getArgument(0);
            ReflectionTestUtils.setField(show, "id", 7L);
            return show;
        });
    }

    @Test
    void createsShowAndEpisodesAndMarksExistingNfos() {
        when(nfoFiles.findShowNfo(Path.of(SHOW_DIRECTORY)))
                .thenReturn(Optional.of(new TvNfoState(Path.of(SHOW_DIRECTORY, "tvshow.nfo"), NFO_MODIFIED_AT)));
        when(nfoFiles.findEpisodeNfo(Path.of(EPISODE_FILE))).thenReturn(Optional.empty());
        TvShowImportSession session = service.startSession(List.of(SHOW_DIRECTORY));

        ImportedTvShow show = session.importShow(showImport("Lost"));
        TvImportResult first = session.importEpisode(show, episodeImport(1, "Pilot (1)", STREAMS));
        TvImportResult second = session.importEpisode(show, episodeImport(2, "Pilot (2)", StreamDetails.EMPTY));

        assertThat(show.id()).isEqualTo(7L);
        assertThat(show.result()).isEqualTo(new TvImportResult(true, true));
        assertThat(first).isEqualTo(new TvImportResult(true, false));
        assertThat(second.created()).isTrue();
        verify(episodeRepository, times(2)).save(any());
    }

    @Test
    void updatesKnownShowsAndEpisodesAndKeepsTheirStreamsWithoutNewOnes() {
        TvShow known = new TvShow(3, SHOW_DIRECTORY, "Lost", null);
        ReflectionTestUtils.setField(known, "id", 7L);
        Episode knownEpisode = new Episode(7, EPISODE_FILE, 1, 1, "Alt");
        knownEpisode.applyExtras(new EpisodeExtras(List.of(), List.of(), List.of(), List.of(), null, null, STREAMS));
        when(showRepository.findByDirectoryPathHashIn(anyCollection())).thenReturn(List.of(known));
        when(episodeRepository.findByTvShowIdIn(anyCollection())).thenReturn(List.of(knownEpisode));
        TvShowImportSession session = service.startSession(List.of(SHOW_DIRECTORY, SHOW_DIRECTORY));

        ImportedTvShow show = session.importShow(showImport("Lost – Verschollen"));
        TvImportResult result = session.importEpisode(show, episodeImport(1, "Pilot (1)", StreamDetails.EMPTY));

        assertThat(show.result().created()).isFalse();
        assertThat(known.getTitle()).isEqualTo("Lost – Verschollen");
        assertThat(result).isEqualTo(new TvImportResult(false, false));
        assertThat(knownEpisode.getTitle()).isEqualTo("Pilot (1)");
        assertThat(knownEpisode.extras().streams()).isEqualTo(STREAMS);
        assertThat(knownEpisode.extras().playCount()).isEqualTo(2);
        assertThat(knownEpisode.getAddedAt()).isEqualTo(ADDED_AT);
        verify(showRepository, never()).save(any());
        verify(episodeRepository, never()).save(any());
    }

    @Test
    void marksEpisodeNfoAsSynchronized() {
        when(nfoFiles.findEpisodeNfo(Path.of(EPISODE_FILE)))
                .thenReturn(Optional.of(new TvNfoState(Path.of(SHOW_DIRECTORY, "Lost S01E01E02.nfo"), NFO_MODIFIED_AT)));
        TvShowImportSession session = service.startSession(List.of(SHOW_DIRECTORY));
        ImportedTvShow show = session.importShow(showImport("Lost"));

        TvImportResult result = session.importEpisode(show, episodeImport(1, "Pilot (1)", STREAMS));

        assertThat(result.nfoPresent()).isTrue();
    }

    @Test
    void loadsKnownShowsInChunks() {
        List<String> directories = IntStream.range(0, TvShowImportService.QUERY_CHUNK_SIZE + 1)
                .mapToObj(index -> SHOW_DIRECTORY + index)
                .toList();

        service.startSession(directories);

        verify(showRepository, times(2)).findByDirectoryPathHashIn(anyCollection());
        verify(episodeRepository, never()).findByTvShowIdIn(anyCollection());
    }

    private static TvShowImport showImport(String title) {
        return new TvShowImport(3, SHOW_DIRECTORY, new TvShowMetadata(title, null, null, null, null, null, null, null,
                null, List.of(), List.of(), List.of(), null, null, null, null), null);
    }

    private static EpisodeImport episodeImport(int number, String title, StreamDetails streams) {
        return new EpisodeImport(EPISODE_FILE, 1, number,
                new EpisodeMetadata(title, null, null, null, null, null, null, null, null),
                new EpisodeExtras(List.of(), List.of(), List.of(), List.of(), 2, null, streams), ADDED_AT);
    }
}
