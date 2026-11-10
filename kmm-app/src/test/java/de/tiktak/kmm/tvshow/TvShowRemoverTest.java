package de.tiktak.kmm.tvshow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import de.tiktak.kmm.removal.RemovalFiles;
import de.tiktak.kmm.tvshow.EpisodesRemovedEvent.RemovedEpisode;

@ExtendWith(MockitoExtension.class)
class TvShowRemoverTest {

    private static final long SHOW_ID = 7;
    private static final String DIRECTORY = "/serien/ALF";

    @Mock
    private TvShowRepository showRepository;

    @Mock
    private EpisodeRepository episodeRepository;

    @Mock
    private ApplicationEventPublisher events;

    private TvShowRemover remover;
    private TvShow show;

    @BeforeEach
    void setUp() {
        remover = new TvShowRemover(showRepository, episodeRepository, events);
        show = new TvShow(3, DIRECTORY, "ALF", 1986);
        ReflectionTestUtils.setField(show, "id", SHOW_ID);
        show.applyExtras(new TvShowExtras(List.of(), List.of(), new java.util.TreeMap<>(Map.of(1, "Erste", 2,
                "Zweite"))));
    }

    @Test
    void removesEpisodesAndTheDetailsOfSeasonsWithoutEpisodesLeft() {
        Episode first = episode(1, 1, 1);
        Episode second = episode(2, 2, 1);
        Episode remaining = episode(3, 2, 2);
        List<RemovedEpisode> removed = List.of(new RemovedEpisode(first.getFilePath(), 1, 1),
                new RemovedEpisode(second.getFilePath(), 2, 1));
        when(episodeRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(first, second));
        when(showRepository.findById(SHOW_ID)).thenReturn(Optional.of(show));
        when(episodeRepository.findByTvShowIdOrderBySeasonAscEpisodeAsc(SHOW_ID)).thenReturn(List.of(remaining));

        remover.remove(new TvShowRemoval(SHOW_ID, "ALF", DIRECTORY, "ALF", List.of(1L, 2L), removed, Set.of(1, 2),
                false, new RemovalFiles(), List.of()));

        verify(episodeRepository).deleteAll(List.of(first, second));
        assertThat(show.extras().seasonNames()).containsOnlyKeys(2);
        verify(events).publishEvent(new EpisodesRemovedEvent("ALF", removed));
    }

    @Test
    void removesAWholeShowWithAllEpisodes() {
        List<Episode> episodes = List.of(episode(1, 1, 1), episode(2, 1, 2));
        when(episodeRepository.findByTvShowIdOrderBySeasonAscEpisodeAsc(SHOW_ID)).thenReturn(episodes);
        when(showRepository.findById(SHOW_ID)).thenReturn(Optional.of(show));

        remover.remove(new TvShowRemoval(SHOW_ID, "ALF", DIRECTORY, "ALF", List.of(1L, 2L), List.of(), Set.of(1),
                true, new RemovalFiles(), List.of()));

        verify(episodeRepository).deleteAll(episodes);
        verify(showRepository).delete(show);
        verify(events).publishEvent(new TvShowRemovedEvent("ALF", DIRECTORY));
    }

    private static Episode episode(long id, int season, int number) {
        Episode episode = new Episode(SHOW_ID, DIRECTORY + "/S%02dE%02d.avi".formatted(season, number), season, number,
                "Episode " + id);
        ReflectionTestUtils.setField(episode, "id", id);
        return episode;
    }
}
