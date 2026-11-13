package de.tiktak.kmm.tvshow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.nfo.NfoException;
import de.tiktak.kmm.plugin.ScraperRegistry;
import de.tiktak.kmm.plugin.api.CastMember;
import de.tiktak.kmm.plugin.api.EpisodeResult;
import de.tiktak.kmm.plugin.api.ScraperException;
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
        when(scraper.episodeDetails(any())).thenThrow(new ScraperException("TVDB weg"));
        NfoException unwritable = mock(NfoException.class);
        when(nfoFiles.writeShow(any(), any())).thenThrow(unwritable);
        when(nfoFiles.writeEpisodes(any(), any())).thenThrow(unwritable);
        TvShowScrapeService service = new TvShowScrapeService(scraperRegistry, showRepository, episodeRepository,
                nfoFiles, mock(TvNfoSync.class), new TvShowResultCache(Clock.systemUTC()),
                mock(TvShowMetadataService.class),
                new TvScrapingProperties("tvdb"), mock(ApplicationEventPublisher.class), Clock.systemUTC());

        EpisodeFillResult result = service.fillFromProvider(7, "tvdb", "73739");

        assertThat(result.updated()).isZero();
        assertThat(result.unchanged()).isZero();
        assertThat(result.failedFiles()).containsExactly(Path.of(SHOW_DIRECTORY, "tvshow.nfo").toString(),
                episode.getFilePath());
        assertThat(episode.getTitle()).isEqualTo("Episode 1");
        assertThat(show.extras().actors()).isEmpty();
    }

    @Test
    void reportsScrapersThatDoNotKnowTheShowWhenLookingForThumbs() {
        TvShow show = new TvShow(1, SHOW_DIRECTORY, "Lost", null);
        ReflectionTestUtils.setField(show, "id", 7L);
        Episode episode = new Episode(7, Path.of(SHOW_DIRECTORY, "Lost S01E01.mkv").toString(), 1, 1, "Pilot");
        when(episodeRepository.findById(3L)).thenReturn(Optional.of(episode));
        when(showRepository.findById(7L)).thenReturn(Optional.of(show));
        when(scraperRegistry.tvShowScrapers()).thenReturn(List.of(scraper));
        when(scraper.displayName()).thenReturn("TheTVDB");
        when(scraper.scrape(any())).thenReturn(Optional.empty());
        TvShowScrapeService service = new TvShowScrapeService(scraperRegistry, showRepository, episodeRepository,
                nfoFiles, mock(TvNfoSync.class), new TvShowResultCache(Clock.systemUTC()),
                mock(TvShowMetadataService.class),
                new TvScrapingProperties("tvdb"), mock(ApplicationEventPublisher.class), Clock.systemUTC());

        EpisodeThumbs thumbs = service.episodeThumbs(3);

        assertThat(thumbs.thumbs()).isEmpty();
        assertThat(thumbs.warnings()).singleElement().satisfies(warning -> {
            assertThat(warning.key()).isEqualTo("tvShow.thumbs.scraperFailed");
            assertThat(warning.args()).containsEntry("scraper", "TheTVDB");
            assertThat(((LocalizedText) warning.args().get("message")).key()).isEqualTo("tvShow.notIdentified.detail");
        });
    }

    @Test
    void autoScrapesWithTheFirstReadyScraperWhenThePreferredOneIsMissing() {
        TvShow show = new TvShow(1, SHOW_DIRECTORY, "Lost", null);
        ReflectionTestUtils.setField(show, "id", 7L);
        when(showRepository.findById(7L)).thenReturn(Optional.of(show));
        TvShowScraper unconfigured = mock(TvShowScraper.class);
        when(unconfigured.missingConfiguration()).thenReturn(Optional.of(LocalizedText.literal("Key fehlt")));
        when(scraperRegistry.tvShowScrapers()).thenReturn(List.of(unconfigured, scraper));
        when(scraper.id()).thenReturn("tmdb");
        when(scraper.scrape(any())).thenReturn(Optional.empty());
        TvShowMetadataService metadataService = mock(TvShowMetadataService.class);
        when(metadataService.currentMetadata(7)).thenReturn(show.metadata());
        TvShowScrapeService service = new TvShowScrapeService(scraperRegistry, showRepository, episodeRepository,
                nfoFiles, mock(TvNfoSync.class), new TvShowResultCache(Clock.systemUTC()), metadataService,
                new TvScrapingProperties("tvdb"), mock(ApplicationEventPublisher.class), Clock.systemUTC());

        assertThat(service.scrapeMissing(7).status()).isEqualTo(TvShowAutoScrapeOutcome.Status.NO_MATCH);

        when(scraperRegistry.tvShowScrapers()).thenReturn(List.of(unconfigured));
        assertThat(service.scrapeMissing(7).status()).isEqualTo(TvShowAutoScrapeOutcome.Status.NO_SCRAPER);
    }

    @Test
    void scrapesOnlyTheEpisodesOfOneFileAndLeavesTheShowNfoAlone() {
        TvShow show = new TvShow(1, SHOW_DIRECTORY, "Lost", null);
        ReflectionTestUtils.setField(show, "id", 7L);
        Episode first = new Episode(7, Path.of(SHOW_DIRECTORY, "Lost S01E01.mkv").toString(), 1, 1, "Episode 1");
        Episode second = new Episode(7, Path.of(SHOW_DIRECTORY, "Lost S01E02.mkv").toString(), 1, 2, "Episode 2");
        ReflectionTestUtils.setField(first, "id", 1L);
        ReflectionTestUtils.setField(second, "id", 2L);
        when(showRepository.findById(7L)).thenReturn(Optional.of(show));
        when(episodeRepository.findByTvShowIdOrderBySeasonAscEpisodeAsc(7)).thenReturn(List.of(first, second));
        when(scraperRegistry.tvShowScrapers()).thenReturn(List.of(scraper));
        when(scraper.id()).thenReturn("tvdb");
        when(scraper.scrape(any())).thenReturn(Optional.of(TvShowResult.builder().title("Lost")
                .uniqueIds(Map.of("tvdb", "73739"))
                .actors(List.of(new CastMember("Matthew Fox", null, null)))
                .episodes(List.of(EpisodeResult.builder().season(1).episode(1).title("Pilot").build(),
                        EpisodeResult.builder().season(1).episode(2).title("Pilot 2").build()))
                .build()));
        when(scraper.episodeDetails(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(nfoFiles.writeEpisodes(any(), any()))
                .thenReturn(new TvNfoState(Path.of(SHOW_DIRECTORY, "Lost S01E02.nfo"), Instant.EPOCH));
        TvShowMetadataService metadataService = mock(TvShowMetadataService.class);
        when(metadataService.currentMetadata(7)).thenReturn(show.metadata());
        TvShowScrapeService service = new TvShowScrapeService(scraperRegistry, showRepository, episodeRepository,
                nfoFiles, mock(TvNfoSync.class), new TvShowResultCache(Clock.systemUTC()), metadataService,
                new TvScrapingProperties("tvdb"), mock(ApplicationEventPublisher.class), Clock.systemUTC());

        TvShowAutoScrapeOutcome outcome = service.scrapeMissing(7, EpisodeScope.file(second.getFilePath()));

        assertThat(outcome.episodesUpdated()).isEqualTo(1);
        assertThat(second.getTitle()).isEqualTo("Pilot 2");
        assertThat(first.getTitle()).isEqualTo("Episode 1");
        verify(nfoFiles).writeEpisodes(eq(second.file()), any());
        verify(nfoFiles, never()).writeShow(any(), any());
    }
}
