package de.tiktak.kmm.plugin.tmdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.MovieSetResult;
import de.tiktak.kmm.plugin.api.ScraperException;

class TmdbMovieSetScraperTest {

    private static final String API_KEY = "0123456789abcdef0123456789abcdef";

    private FakeTmdbServer tmdb;
    private TmdbMovieSetScraper scraper;

    @BeforeEach
    void setUp() {
        tmdb = new FakeTmdbServer()
                .respond("/3/search/collection", "search-collection-matrix.json")
                .respond("/3/collection/2344", "collection-2344.json");
        scraper = new TmdbMovieSetScraper();
        scraper.initialize(new TestPluginContext().with("api-key", API_KEY).with("base-url", tmdb.baseUrl().toString()));
    }

    @AfterEach
    void tearDown() {
        tmdb.close();
    }

    @Test
    void describesItself() {
        assertThat(scraper.id()).isEqualTo("tmdb");
        assertThat(scraper.supportedMediaTypes()).containsExactly(MediaType.MOVIE_SET);
    }

    @Test
    void searchesCollections() {
        var results = scraper.searchSets("Matrix");

        assertThat(results).hasSize(2);
        assertThat(results.getFirst()).satisfies(matrix -> {
            assertThat(matrix.providerId()).isEqualTo("2344");
            assertThat(matrix.title()).isEqualTo("Matrix Filmreihe");
            assertThat(matrix.originalTitle()).isEqualTo("The Matrix Collection");
            assertThat(matrix.overview()).isEqualTo("Neo.");
            assertThat(matrix.posterUrl()).isEqualTo(URI.create("https://image.tmdb.org/t/p/w185/matrix.jpg"));
        });
        assertThat(results.get(1).posterUrl()).isNull();
        assertThat(tmdb.requests().getFirst().query()).contains("query=Matrix", "language=de-DE");
    }

    @Test
    void fetchesACollectionAndAsksInEnglishForAMissingOverview() {
        assertThat(scraper.fetchSet("2344")).contains(new MovieSetResult("2344", "Matrix Filmreihe", null));

        assertThat(tmdb.requests()).hasSize(2);
        assertThat(tmdb.requests().get(1).query()).contains("language=" + TmdbMovieSetScraper.FALLBACK_LANGUAGE);
    }

    @Test
    void returnsNothingForUnknownOrInvalidIds() {
        assertThat(scraper.fetchSet("404")).isEmpty();
        assertThat(scraper.fetchSet("../x")).isEmpty();
    }

    @Test
    void needsAnApiKey() {
        TmdbMovieSetScraper unconfigured = new TmdbMovieSetScraper();
        unconfigured.initialize(new TestPluginContext().with("base-url", tmdb.baseUrl().toString()));

        assertThatThrownBy(() -> unconfigured.searchSets("Matrix")).isInstanceOf(ScraperException.class);
    }
}
