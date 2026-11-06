package de.tiktak.kmm.plugin.tmdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.plugin.api.CollectionReference;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.ScrapeRequest;
import de.tiktak.kmm.plugin.api.ScraperException;
import de.tiktak.kmm.plugin.api.SearchQuery;

class TmdbMetadataScraperTest {

    private static final String API_KEY = "0123456789abcdef0123456789abcdef";

    private FakeTmdbServer tmdb;
    private TmdbMetadataScraper scraper;

    @BeforeEach
    void setUp() {
        tmdb = new FakeTmdbServer()
                .respond("/3/search/movie", "search-heat.json")
                .respond("/3/movie/949", "movie-949.json")
                .respond("/3/find/tt0113277", "find-tt0113277.json");
        scraper = initializedScraper(API_KEY);
    }

    @AfterEach
    void tearDown() {
        tmdb.close();
    }

    @Test
    void describesItself() {
        assertThat(scraper.id()).isEqualTo("tmdb");
        assertThat(scraper.displayName()).contains("TMDB");
        assertThat(scraper.supportedMediaTypes()).containsExactly(MediaType.MOVIE);
        assertThat(scraper.missingConfiguration()).isEmpty();
    }

    @Test
    void searchesMoviesInConfiguredLanguage() {
        var results = scraper.search(new SearchQuery(MediaType.MOVIE, "Heat", 1995));

        assertThat(results).hasSize(3);
        assertThat(results.getFirst()).satisfies(heat -> {
            assertThat(heat.providerId()).isEqualTo("949");
            assertThat(heat.title()).isEqualTo("Heat");
            assertThat(heat.year()).isEqualTo(1995);
            assertThat(heat.overview()).isEqualTo("Ein Meisterdieb und ein Polizist.");
            assertThat(heat.posterUrl()).isEqualTo(URI.create("https://image.tmdb.org/t/p/w185/heat.jpg"));
        });
        assertThat(results.get(2).year()).isNull();
        assertThat(results.get(2).overview()).isNull();
        assertThat(tmdb.requests().getFirst().query())
                .contains("query=Heat", "year=1995", "language=de-DE", "include_adult=false", "api_key=" + API_KEY);
        assertThat(tmdb.requests().getFirst().userAgent()).isEqualTo(TestPluginContext.USER_AGENT);
    }

    @Test
    void searchesWithoutYear() {
        scraper.search(new SearchQuery(MediaType.MOVIE, "Heat", null));

        assertThat(tmdb.requests().getFirst().query()).doesNotContain("year=");
    }

    @Test
    void returnsNoSearchResultsForUnsupportedMediaType() {
        assertThat(scraper.search(new SearchQuery(MediaType.TV_SHOW, "Lost", null))).isEmpty();
        assertThat(tmdb.requests()).isEmpty();
    }

    @Test
    void fetchesAllMetadataIncludingGermanCertification() {
        var metadata = scraper.fetch("949").orElseThrow();

        assertThat(metadata.title()).isEqualTo("Heat");
        assertThat(metadata.originalTitle()).isEqualTo("Heat");
        assertThat(metadata.year()).isEqualTo(1995);
        assertThat(metadata.premiered()).isEqualTo(LocalDate.of(1995, 12, 15));
        assertThat(metadata.plot()).startsWith("Der Berufsverbrecher");
        assertThat(metadata.tagline()).isEqualTo("A Los Angeles Crime Saga");
        assertThat(metadata.runtime()).isEqualTo(170);
        assertThat(metadata.certification()).isEqualTo("FSK 16");
        assertThat(metadata.genres()).containsExactly("Krimi", "Drama", "Thriller");
        assertThat(metadata.uniqueIds()).containsEntry("imdb", "tt0113277").containsEntry("tmdb", "949");
        assertThat(tmdb.requests().getFirst().query()).contains("append_to_response=release_dates");
    }

    @Test
    void usesCertificationOfConfiguredCountryWithPrefix() {
        scraper = initializedScraper(new TestPluginContext().with("api-key", API_KEY)
                .with("base-url", tmdb.baseUrl().toString())
                .with("certification-country", "US").with("certification-prefix", "Rated "));

        assertThat(scraper.fetch("949").orElseThrow().certification()).isEqualTo("Rated R");
    }

    @Test
    void knowsTheCollectionOfAMovie() {
        tmdb.respond("/3/movie/603", "movie-603.json");

        assertThat(scraper.fetch("603").orElseThrow().collection())
                .isEqualTo(new CollectionReference("2344", "Matrix Filmreihe"));
        assertThat(scraper.fetch("949").orElseThrow().collection()).isNull();
    }

    @Test
    void returnsNothingForUnknownOrInvalidId() {
        assertThat(scraper.fetch("404")).isEmpty();
        assertThat(scraper.fetch("../account")).isEmpty();
        assertThat(tmdb.requests()).hasSize(1);
    }

    @Test
    void scrapesByKnownTmdbId() {
        var result = scraper.scrape(new ScrapeRequest(MediaType.MOVIE, "egal", null, Map.of("tmdb", "949")));

        assertThat(result).hasValueSatisfying(metadata -> assertThat(metadata.runtime()).isEqualTo(170));
        assertThat(tmdb.requests()).extracting(FakeTmdbServer.HttpExchangeRecord::path).containsExactly("/3/movie/949");
    }

    @Test
    void scrapesByImdbId() {
        var result = scraper.scrape(new ScrapeRequest(MediaType.MOVIE, "egal", null, Map.of("imdb", "tt0113277")));

        assertThat(result).isPresent();
        assertThat(tmdb.requests()).extracting(FakeTmdbServer.HttpExchangeRecord::path)
                .containsExactly("/3/find/tt0113277", "/3/movie/949");
    }

    @Test
    void ignoresInvalidImdbId() {
        assertThat(scraper.scrape(new ScrapeRequest(MediaType.MOVIE, "egal", null, Map.of("imdb", "x/../y"))))
                .isEmpty();
        assertThat(tmdb.requests()).isEmpty();
    }

    @ParameterizedTest(name = "{0} ({1}) -> {2}")
    @CsvSource(nullValues = "null", value = {"Heat, 1995, 1995", "heat, 1986, 1986", "Heat, null, 1995"})
    void scrapesUnambiguousSearchMatch(String title, Integer year, int expectedYear) {
        tmdb.respond("/3/movie/12345", "movie-949.json");

        var result = scraper.scrape(new ScrapeRequest(MediaType.MOVIE, title, year, null));

        assertThat(result).isPresent();
        String expectedId = expectedYear == 1995 ? "949" : "12345";
        assertThat(tmdb.requests().getLast().path()).isEqualTo("/3/movie/" + expectedId);
    }

    @Test
    void refusesUncertainSearchMatch() {
        assertThat(scraper.scrape(new ScrapeRequest(MediaType.MOVIE, "Heat", 2020, null))).isEmpty();
        assertThat(scraper.scrape(new ScrapeRequest(MediaType.MOVIE, "Hea", null, null))).isEmpty();
    }

    @Test
    void scrapesNothingForUnsupportedMediaType() {
        assertThat(scraper.scrape(new ScrapeRequest(MediaType.TV_SHOW, "Lost", null, null))).isEmpty();
    }

    @Test
    void sendsLongTokenAsBearerHeaderInsteadOfQueryParameter() {
        String readToken = "eyJhbGciOiJIUzI1NiJ9." + "x".repeat(100);
        scraper = initializedScraper(readToken);

        scraper.fetch("949");

        assertThat(tmdb.requests().getFirst().authorization()).isEqualTo("Bearer " + readToken);
        assertThat(tmdb.requests().getFirst().query()).doesNotContain("api_key");
    }

    @Test
    void reportsMissingApiKey() {
        scraper = initializedScraper("  ");

        assertThat(scraper.missingConfiguration()).contains(AbstractTmdbScraper.MISSING_API_KEY);
        assertThatThrownBy(() -> scraper.search(new SearchQuery(MediaType.MOVIE, "Heat", null)))
                .isInstanceOfSatisfying(ScraperException.class, e -> assertThat(e.localizedText())
                        .isEqualTo(LocalizedText.of("scraper.apiKeyMissing").with("provider", "TMDB")));
        assertThat(tmdb.requests()).isEmpty();
    }

    @Test
    void rejectsUseBeforeInitialization() {
        var uninitialized = new TmdbMetadataScraper();

        assertThat(uninitialized.missingConfiguration()).isPresent();
        assertThatIllegalStateException().isThrownBy(() -> uninitialized.fetch("949"));
    }

    @ParameterizedTest(name = "HTTP {0}")
    @CsvSource(delimiter = '|', value = {
            "401 | scraper.apiKeyRejected",
            "429 | scraper.rateLimitReached",
            "500 | scraper.httpError"})
    void translatesErrorResponses(int status, String expectedKey) {
        tmdb.respondWithStatus("/3/movie/949", status);

        assertThatThrownBy(() -> scraper.fetch("949"))
                .isInstanceOfSatisfying(ScraperException.class, e -> {
                    assertThat(e.localizedText().key()).isEqualTo(expectedKey);
                    assertThat(e.localizedText().args()).containsEntry("provider", "TMDB");
                });
    }

    @Test
    void reportsUnreachableServer() {
        URI unreachable = tmdb.baseUrl();
        tmdb.close();
        scraper = initializedScraper(new TestPluginContext().with("api-key", API_KEY)
                .with("base-url", unreachable.toString()));

        assertThatThrownBy(() -> scraper.fetch("949"))
                .isInstanceOfSatisfying(ScraperException.class, e ->
                        assertThat(e.localizedText().key()).isEqualTo("scraper.unreachable"))
                .hasMessageContaining("TMDB cannot be reached");
    }

    @Test
    void reportsUnexpectedResponse() {
        tmdb.respond("/3/movie/949", "invalid.json");

        assertThatThrownBy(() -> scraper.fetch("949"))
                .isInstanceOfSatisfying(ScraperException.class, e -> assertThat(e.localizedText())
                        .isEqualTo(LocalizedText.of("scraper.unexpectedResponse").with("provider", "TMDB")));
    }

    private TmdbMetadataScraper initializedScraper(String apiKey) {
        return initializedScraper(new TestPluginContext().with("api-key", apiKey)
                .with("base-url", tmdb.baseUrl().toString()));
    }

    private static TmdbMetadataScraper initializedScraper(TestPluginContext context) {
        var tmdbScraper = new TmdbMetadataScraper();
        tmdbScraper.initialize(context);
        return tmdbScraper;
    }
}
