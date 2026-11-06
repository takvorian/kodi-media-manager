package de.tiktak.kmm.movie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.job.JobContext;
import de.tiktak.kmm.job.JobService;
import de.tiktak.kmm.plugin.api.MovieSetResult;

/**
 * "Complete all sets" in {@link MovieSetJobService}.
 */
@ExtendWith(MockitoExtension.class)
class MovieSetCompletionTest {

    private static final MovieSetSummary MATRIX = new MovieSetSummary(1L, "Matrix Filmreihe", null, "2344",
            SetSortMethod.YEAR, 3);
    private static final MovieSetSummary MARPLE = new MovieSetSummary(2L, "Miss Marple", "Beschreibung", "1234",
            SetSortMethod.YEAR, 4);
    private static final MovieSetSummary PRIVATE = new MovieSetSummary(3L, "Urlaub", null, null, SetSortMethod.YEAR,
            2);

    @Mock
    private MovieSetService setService;

    @Mock
    private MovieSetMembership membership;

    @Mock
    private JobService jobService;

    @Mock
    private MovieSetScrapeService scrapeService;

    @Mock
    private MovieSetArtworkFiller artworkFiller;

    @InjectMocks
    private MovieSetJobService setJobs;

    private final JobContext context = mock(JobContext.class);

    @Test
    void fillsMissingOverviewsAndImagesAndKeepsExistingOnes() {
        when(setService.findAll()).thenReturn(List.of(MATRIX, MARPLE, PRIVATE));
        when(scrapeService.fetch("2344")).thenReturn(Optional.of(new MovieSetResult("2344", "The Matrix", "Neo.")));
        when(setService.update(1L, new MovieSetData("Matrix Filmreihe", "Neo.", "2344", SetSortMethod.YEAR)))
                .thenReturn(List.of(10L, 11L));
        List<LocalizedText> warnings = List.of(LocalizedText.literal("fanart.tv: aus"));
        when(artworkFiller.fillMissingArtwork(1L)).thenReturn(new MovieSetArtworkFiller.Fill(2, warnings));
        when(artworkFiller.fillMissingArtwork(2L)).thenReturn(new MovieSetArtworkFiller.Fill(1, warnings));

        MovieSetCompletionResult result = setJobs.complete(context);

        assertThat(result).isEqualTo(new MovieSetCompletionResult(1, 3, 1, warnings));
        verify(membership).rewrite(10L);
        verify(membership).rewrite(11L);
        verify(scrapeService, never()).fetch("1234");
        verify(artworkFiller, never()).fillMissingArtwork(3L);
        verify(context).reportProgress(3, 3);
    }

    @Test
    void leavesTheOverviewEmptyIfTheProviderHasNoneAndContinuesAfterProblems() {
        when(setService.findAll()).thenReturn(List.of(MATRIX, MARPLE));
        when(scrapeService.fetch("2344")).thenReturn(Optional.of(new MovieSetResult("2344", "The Matrix", null)));
        when(artworkFiller.fillMissingArtwork(1L))
                .thenThrow(ScraperProblemException.noMovieSetScraper());
        when(artworkFiller.fillMissingArtwork(2L)).thenReturn(new MovieSetArtworkFiller.Fill(0, List.of()));

        MovieSetCompletionResult result = setJobs.complete(context);

        assertThat(result.overviews()).isZero();
        assertThat(result.problems()).containsExactly(LocalizedText.of("movieSet.failure")
                .with("set", "Matrix Filmreihe")
                .with("reason", LocalizedText.of("movie.scraper.noMovieSetScraper.detail")));
        verify(setService, never()).update(anyLong(), any());
        verify(artworkFiller).fillMissingArtwork(2L);
    }
}
