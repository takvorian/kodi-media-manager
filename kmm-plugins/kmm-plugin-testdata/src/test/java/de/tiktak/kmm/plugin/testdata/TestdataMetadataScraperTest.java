package de.tiktak.kmm.plugin.testdata;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.ScrapeRequest;
import de.tiktak.kmm.plugin.api.SearchQuery;

class TestdataMetadataScraperTest {

    private final TestdataMetadataScraper scraper = new TestdataMetadataScraper();

    @Test
    void describesItself() {
        assertThat(scraper.id()).isEqualTo(TestdataMetadataScraper.ID);
        assertThat(scraper.displayName()).isEqualTo(TestdataMetadataScraper.DISPLAY_NAME);
        assertThat(scraper.supportedMediaTypes()).containsExactly(MediaType.MOVIE);
    }

    @Test
    void returnsRequestedTitleForMovies() {
        var result = scraper.scrape(new ScrapeRequest(MediaType.MOVIE, "Heat", 1995, null));

        assertThat(result).hasValueSatisfying(metadata -> {
            assertThat(metadata.title()).isEqualTo("Heat");
            assertThat(metadata.year()).isEqualTo(1995);
            assertThat(metadata.uniqueIds()).containsEntry(TestdataMetadataScraper.UNIQUE_ID_KEY, "Heat");
        });
    }

    @Test
    void returnsNothingForUnsupportedMediaType() {
        assertThat(scraper.scrape(new ScrapeRequest(MediaType.TV_SHOW, "Lost", null, null))).isEmpty();
        assertThat(scraper.search(new SearchQuery(MediaType.TV_SHOW, "Lost", null))).isEmpty();
    }

    @Test
    void findsTitleAndFetchesItById() {
        var hits = scraper.search(new SearchQuery(MediaType.MOVIE, "Heat", 1995));

        assertThat(hits).singleElement().satisfies(hit -> assertThat(hit.providerId()).isEqualTo("Heat"));
        assertThat(scraper.fetch("Heat")).hasValueSatisfying(metadata -> assertThat(metadata.title()).isEqualTo("Heat"));
    }
}
