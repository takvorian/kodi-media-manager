package de.tiktak.kmm.tvshow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import de.tiktak.kmm.nfo.NfoException;
import de.tiktak.kmm.plugin.ScraperRegistry;
import de.tiktak.kmm.plugin.api.CastMember;
import de.tiktak.kmm.plugin.api.EpisodeResult;
import de.tiktak.kmm.plugin.api.TvShowResult;
import de.tiktak.kmm.plugin.api.TvShowScraper;

@ExtendWith(MockitoExtension.class)
class TvShowScrapeServiceTest {

    private static final String SHOW_DIRECTORY = Path.of("/serien/Lost").toAbsolutePath().toString();

    @Mock
    private ScraperRegistry scraperRegistry;

    @Mock
    private TvShowRepository showRepository;

    @Mock
    private EpisodeRepository episodeRepository;

    @Mock
    private TvShowNfoFiles nfoFiles;

    @Mock
    private TvShowScraper scraper;

    @Test
    void keepsShowAndEpisodesUnchangedWhenNfosCannotBeWritten() {
        TvShow show = new TvShow(1, SHOW_DIRECTORY, "Lost", null);
        ReflectionTestUtils.setField(show, "id", 7L);
        Episode episode = new Episode(7, Path.of(SHOW_DIRECTORY, "Lost S01E01.mkv").toString(), 1, 1, "Episode 1");
        when(showRepository.findById(7L)).thenReturn(Optional.of(show));
        when(episodeRepository.findByTvShowIdOrderBySeasonAscEpisodeAsc(7)).thenReturn(List.of(episode));
        when(scraperRegistry.tvShowScraper("tvdb")).thenReturn(Optional.of(scraper));
        when(scraper.id()).thenReturn("tvdb");
        when(scraper.fetch("73739")).thenReturn(Optional.of(TvShowResult.builder().title("Lost")
                .actors(List.of(new CastMember("Matthew Fox", null, null)))
                .episodes(List.of(EpisodeResult.builder().season(1).episode(1).title("Pilot")
                        .aired(LocalDate.of(2004, 9, 22)).build()))
                .build()));
        NfoException unwritable = mock(NfoException.class);
        when(nfoFiles.writeShow(any(), any())).thenThrow(unwritable);
        when(nfoFiles.writeEpisodes(any(), any())).thenThrow(unwritable);
        TvShowScrapeService service = new TvShowScrapeService(scraperRegistry, showRepository, episodeRepository,
                nfoFiles, new TvShowResultCache(Clock.systemUTC()), Clock.systemUTC());

        EpisodeFillResult result = service.fillFromProvider(7, "tvdb", "73739");

        assertThat(result.updated()).isZero();
        assertThat(result.unchanged()).isZero();
        assertThat(result.failedFiles()).containsExactly(Path.of(SHOW_DIRECTORY, "tvshow.nfo").toString(),
                episode.getFilePath());
        assertThat(episode.getTitle()).isEqualTo("Episode 1");
        assertThat(show.extras().actors()).isEmpty();
    }
}
