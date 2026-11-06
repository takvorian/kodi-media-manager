package de.tiktak.kmm.enrichment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.plugin.ScraperRegistry;
import de.tiktak.kmm.plugin.api.Enrichment;
import de.tiktak.kmm.plugin.api.EnrichmentScraper;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.ProviderRating;
import de.tiktak.kmm.plugin.api.ScraperException;

@ExtendWith(MockitoExtension.class)
class EnrichmentFinderTest {

    private static final Map<String, String> IDS = Map.of("imdb", "tt0113277");
    private static final Enrichment TRAKT = Enrichment.ofRatings(List.of(new ProviderRating("trakt", 8.2, 10, 41)));
    private static final RatingAdapterForTest ADAPTER = new RatingAdapterForTest();

    @Mock
    private ScraperRegistry scraperRegistry;

    @Mock
    private EnrichmentScraper trakt;

    private EnrichmentFinder finder;

    @BeforeEach
    void setUp() {
        finder = new EnrichmentFinder(scraperRegistry);
        lenient().when(trakt.id()).thenReturn("trakt");
        lenient().when(trakt.ratingSources()).thenReturn(Set.of("trakt"));
        lenient().when(trakt.missingConfiguration()).thenReturn(Optional.empty());
        lenient().when(scraperRegistry.enrichmentScrapersFor(MediaType.MOVIE)).thenReturn(List.of(trakt));
    }

    @Test
    void asksOnlyWantedAndConfiguredScrapers() {
        EnrichmentScraper unconfigured = mock(EnrichmentScraper.class);
        when(unconfigured.missingConfiguration()).thenReturn(Optional.of(LocalizedText.literal("OMDb: API-Key fehlt")));
        when(scraperRegistry.enrichmentScrapersFor(MediaType.MOVIE)).thenReturn(List.of(unconfigured, trakt));
        when(trakt.findEnrichment(any())).thenReturn(Optional.of(TRAKT));

        assertThat(finder.find(MediaType.MOVIE, "Heat", 1995, IDS, scraper -> true)).containsExactly(TRAKT);
        assertThat(finder.find(MediaType.MOVIE, "Heat", 1995, IDS, scraper -> false)).isEmpty();
        assertThat(finder.find(MediaType.MOVIE, "Heat", 1995, Map.of(), scraper -> true)).isEmpty();
        verify(unconfigured, never()).findEnrichment(any());
    }

    @Test
    void ignoresUnreachableProviders() {
        when(trakt.findEnrichment(any())).thenThrow(new ScraperException("Trakt ist nicht erreichbar"));

        assertThat(finder.find(MediaType.MOVIE, "Heat", 1995, IDS, scraper -> true)).isEmpty();
    }

    @Test
    void asksAProviderWhenItCanContribute() {
        EnrichmentScraper omdb = mock(EnrichmentScraper.class);
        when(omdb.ratingSources()).thenReturn(Set.of("imdb"));
        when(omdb.providesDetails()).thenReturn(true);

        assertThat(EnrichmentFinder.contributes(false, Set.of("trakt"), false).test(trakt)).isFalse();
        assertThat(EnrichmentFinder.contributes(true, Set.of("trakt"), false).test(trakt)).isTrue();
        assertThat(EnrichmentFinder.contributes(false, Set.of(), false).test(trakt)).isTrue();
        assertThat(EnrichmentFinder.contributes(false, Set.of("imdb"), true).test(omdb)).isTrue();
        assertThat(EnrichmentFinder.contributes(false, Set.of("imdb"), false).test(omdb)).isFalse();
    }

    @Test
    void asksProvidersWithoutRatingsOnlyForEmptyFields() {
        EnrichmentScraper moviepilot = mock(EnrichmentScraper.class);
        when(moviepilot.ratingSources()).thenReturn(Set.of());
        when(moviepilot.providesDetails()).thenReturn(true);

        assertThat(EnrichmentFinder.contributes(true, Set.of(), false).test(moviepilot)).isFalse();
        assertThat(EnrichmentFinder.contributes(false, Set.of(), false).test(moviepilot)).isFalse();
        assertThat(EnrichmentFinder.contributes(false, Set.of(), true).test(moviepilot)).isTrue();
    }

    @Test
    void asksProvidersInTheirOrder() {
        EnrichmentScraper german = mock(EnrichmentScraper.class);
        Enrichment germanEnrichment = Enrichment.ofRatings(List.of());
        when(german.order()).thenReturn(-1);
        when(german.missingConfiguration()).thenReturn(Optional.empty());
        when(german.findEnrichment(any())).thenReturn(Optional.of(germanEnrichment));
        List<EnrichmentScraper> registered = new ArrayList<>(scraperRegistry.enrichmentScrapersFor(MediaType.MOVIE));
        registered.add(german);
        when(scraperRegistry.enrichmentScrapersFor(MediaType.MOVIE)).thenReturn(registered);

        assertThat(finder.find(MediaType.MOVIE, "Heat", 1995, IDS, scraper -> true).getFirst())
                .isSameAs(germanEnrichment);
    }

    @Test
    void mergesRatingsAddingOrReplacingThem() {
        List<String[]> current = List.<String[]>of(new String[] {"IMDB", "7.0", "true"});
        Enrichment found = Enrichment.ofRatings(List.of(new ProviderRating("imdb", 8.3, 10, 1),
                new ProviderRating("trakt", 8.2, 10, 2)));

        assertThat(EnrichmentFinder.mergeRatings(current, List.of(found), false, ADAPTER))
                .extracting(rating -> String.join(",", rating)).containsExactly("IMDB,7.0,true", "trakt,8.2,false");
        assertThat(EnrichmentFinder.mergeRatings(current, List.of(found), true, ADAPTER))
                .extracting(rating -> String.join(",", rating)).containsExactly("imdb,8.3,true", "trakt,8.2,false");
        assertThat(EnrichmentFinder.mergeRatings(List.of(), List.of(found), false, ADAPTER))
                .extracting(rating -> rating[2]).containsExactly("true", "false");
    }

    @Test
    void takesTheFirstValueThatIsThere() {
        Enrichment empty = new Enrichment(null, null, null, null, null, null, null, null, null, null);
        Enrichment full = new Enrichment(null, "Plot", null, null, null, List.of("Crime"), null, null, null, null);

        assertThat(EnrichmentFinder.first(List.of(empty, full), Enrichment::plot)).isEqualTo("Plot");
        assertThat(EnrichmentFinder.first(List.of(empty, full), Enrichment::genres)).containsExactly("Crime");
        assertThat(EnrichmentFinder.first(List.of(empty), Enrichment::runtime)).isNull();
    }

    /** Ratings as {identifier, value, default}. */
    private static final class RatingAdapterForTest implements EnrichmentFinder.RatingAdapter<String[]> {

        @Override
        public String source(String[] rating) {
            return rating[0];
        }

        @Override
        public boolean isDefault(String[] rating) {
            return Boolean.parseBoolean(rating[2]);
        }

        @Override
        public String[] create(ProviderRating rating, boolean defaultRating) {
            return new String[] {rating.source(), String.valueOf(rating.value()), String.valueOf(defaultRating)};
        }
    }
}
