package de.tiktak.kmm.rating;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

import de.tiktak.kmm.plugin.ScraperRegistry;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.ProviderRating;
import de.tiktak.kmm.plugin.api.RatingScraper;
import de.tiktak.kmm.plugin.api.ScrapeRequest;
import de.tiktak.kmm.plugin.api.ScraperException;

@ExtendWith(MockitoExtension.class)
class RatingFinderTest {

    private static final Map<String, String> IDS = Map.of("imdb", "tt0113277");
    private static final ProviderRating TRAKT = new ProviderRating("trakt", 8.2, 10, 41234);

    @Mock
    private ScraperRegistry scraperRegistry;

    @Mock
    private RatingScraper trakt;

    private RatingFinder finder;

    @BeforeEach
    void setUp() {
        finder = new RatingFinder(scraperRegistry);
        lenient().when(trakt.id()).thenReturn("trakt");
        lenient().when(trakt.ratingSource()).thenReturn("trakt");
        lenient().when(trakt.missingConfiguration()).thenReturn(Optional.empty());
        lenient().when(scraperRegistry.ratingScrapersFor(MediaType.MOVIE)).thenReturn(List.of(trakt));
    }

    @Test
    void findsRatingsThatAreMissing() {
        when(trakt.findRating(new ScrapeRequest(MediaType.MOVIE, "Heat", 1995, IDS))).thenReturn(Optional.of(TRAKT));

        assertThat(finder.findMissing(MediaType.MOVIE, "Heat", 1995, IDS, Set.of("themoviedb"))).containsExactly(TRAKT);
    }

    @Test
    void skipsPresentRatingsUnconfiguredScrapersAndEntriesWithoutIds() {
        RatingScraper unconfigured = mock(RatingScraper.class);
        when(unconfigured.ratingSource()).thenReturn("other");
        when(unconfigured.missingConfiguration()).thenReturn(Optional.of("Client-ID fehlt"));
        when(scraperRegistry.ratingScrapersFor(MediaType.MOVIE)).thenReturn(List.of(trakt, unconfigured));

        assertThat(finder.findMissing(MediaType.MOVIE, "Heat", 1995, IDS, Set.of("trakt"))).isEmpty();
        assertThat(finder.findMissing(MediaType.MOVIE, "Heat", 1995, Map.of(), Set.of())).isEmpty();

        verify(trakt, never()).findRating(any());
        verify(unconfigured, never()).findRating(any());
    }

    @Test
    void ignoresUnreachableProviders() {
        when(trakt.findRating(any())).thenThrow(new ScraperException("Trakt ist nicht erreichbar"));

        assertThat(finder.findMissing(MediaType.MOVIE, "Heat", 1995, IDS, Set.of())).isEmpty();
    }
}
