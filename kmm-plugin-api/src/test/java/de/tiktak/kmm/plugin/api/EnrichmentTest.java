package de.tiktak.kmm.plugin.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

class EnrichmentTest {

    @Test
    void replacesMissingListsWithEmptyOnes() {
        Enrichment empty = new Enrichment(null, null, null, null, null, null, null, null, null, null);

        assertThat(empty.ratings()).isEmpty();
        assertThat(empty.genres()).isEmpty();
        assertThat(empty.directors()).isEmpty();
        assertThat(empty.writers()).isEmpty();
        assertThat(empty.actors()).isEmpty();
        assertThat(empty.countries()).isEmpty();
    }

    @Test
    void carriesOnlyRatingsAndProvidesNoDetailsByDefault() {
        ProviderRating trakt = new ProviderRating("trakt", 7.7, 10, 228);
        EnrichmentScraper scraper = new EnrichmentScraper() {
            @Override
            public Set<String> ratingSources() {
                return Set.of("trakt");
            }

            @Override
            public Optional<Enrichment> findEnrichment(ScrapeRequest request) {
                return Optional.of(Enrichment.ofRatings(List.of(trakt)));
            }

            @Override
            public String id() {
                return "trakt";
            }

            @Override
            public String displayName() {
                return "Trakt";
            }

            @Override
            public Set<MediaType> supportedMediaTypes() {
                return Set.of(MediaType.MOVIE);
            }
        };

        assertThat(scraper.providesDetails()).isFalse();
        assertThat(scraper.findEnrichment(null)).hasValueSatisfying(enrichment -> {
            assertThat(enrichment.ratings()).containsExactly(trakt);
            assertThat(enrichment.plot()).isNull();
        });
    }
}
