package de.tiktak.kmm.plugin.omdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.plugin.api.Enrichment;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.PluginContext;
import de.tiktak.kmm.plugin.api.ProviderRating;
import de.tiktak.kmm.plugin.api.ScrapeRequest;
import de.tiktak.kmm.plugin.api.ScraperException;

class OmdbEnrichmentScraperTest {

    private static final String API_KEY = "abc12345";
    private static final String HEAT = """
            {"Title": "Heat", "Year": "1995", "Rated": "R", "Released": "15 Dec 1995", "Runtime": "170 min",
             "Genre": "Action, Crime, Drama", "Director": "Michael Mann",
             "Writer": "Michael Mann (screenplay), Michael Mann (story)", "Actors": "Al Pacino, Robert De Niro",
             "Plot": "A group of high-end professional thieves start to feel the heat.", "Country": "United States",
             "Ratings": [{"Source": "Internet Movie Database", "Value": "8.3/10"},
                         {"Source": "Rotten Tomatoes", "Value": "88%"}, {"Source": "Metacritic", "Value": "76/100"},
                         {"Source": "Unbekannt", "Value": "1"}],
             "Metascore": "76", "imdbRating": "8.3", "imdbVotes": "741,503", "imdbID": "tt0113277",
             "Type": "movie", "Response": "True"}""";
    private static final String SPARSE = """
            {"Title": "Die Feuerzangenbowle", "Rated": "N/A", "Released": "unbekannt", "Runtime": "N/A",
             "Genre": "Comedy", "Director": "N/A", "Writer": "", "Actors": "Heinz Rühmann", "Plot": "N/A",
             "Ratings": [{"Source": "Rotten Tomatoes", "Value": "N/A"}], "imdbRating": "7.7", "imdbVotes": "N/A",
             "Response": "True"}""";

    private final Map<String, String> answers = new ConcurrentHashMap<>();
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private OmdbEnrichmentScraper scraper;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        answers.put("tt0113277", HEAT);
        answers.put("tt0036818", SPARSE);
        answers.put("tt9999999", "{\"Response\": \"False\", \"Error\": \"Incorrect IMDb ID.\"}");
        scraper = scraper(API_KEY);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void readsRatingsAndDetailsByImdbId() {
        Optional<Enrichment> heat = scraper.findEnrichment(request(MediaType.MOVIE, "tt0113277"));

        assertThat(heat).contains(new Enrichment(
                List.of(new ProviderRating("imdb", 8.3, 10, 741503),
                        new ProviderRating("tomatometerallcritics", 88, 100, null),
                        new ProviderRating("metacritic", 76, 100, null)),
                "A group of high-end professional thieves start to feel the heat.", 170, LocalDate.of(1995, 12, 15),
                "R", List.of("Action", "Crime", "Drama"), List.of("Michael Mann"), List.of("Michael Mann"),
                List.of("Al Pacino", "Robert De Niro"), List.of("United States")));
        assertThat(requests).containsExactly("/?i=tt0113277&plot=full&apikey=" + API_KEY);
    }

    @Test
    void leavesUnavailableValuesEmpty() {
        Enrichment sparse = scraper.findEnrichment(request(MediaType.TV_SHOW, "tt0036818")).orElseThrow();

        assertThat(sparse.ratings()).containsExactly(new ProviderRating("imdb", 7.7, 10, null));
        assertThat(sparse.plot()).isNull();
        assertThat(sparse.runtime()).isNull();
        assertThat(sparse.premiered()).isNull();
        assertThat(sparse.certification()).isNull();
        assertThat(sparse.directors()).isEmpty();
        assertThat(sparse.writers()).isEmpty();
        assertThat(sparse.actors()).containsExactly("Heinz Rühmann");
    }

    @Test
    void searchesOnlyWithAValidImdbIdAndForMoviesAndShows() {
        assertThat(scraper.findEnrichment(request(MediaType.MOVIE, "tt9999999"))).isEmpty();
        assertThat(scraper.findEnrichment(request(MediaType.MOVIE, "../x"))).isEmpty();
        assertThat(scraper.findEnrichment(new ScrapeRequest(MediaType.MOVIE, "Heat", null, Map.of("tmdb", "949"))))
                .isEmpty();
        assertThat(scraper.findEnrichment(request(MediaType.TV_EPISODE, "tt0113277"))).isEmpty();
        assertThat(requests).containsExactly("/?i=tt9999999&plot=full&apikey=" + API_KEY);
        assertThat(scraper.ratingSources()).containsExactlyInAnyOrder("imdb", "tomatometerallcritics", "metacritic");
        assertThat(scraper.providesDetails()).isTrue();
        assertThat(scraper.id()).isEqualTo("omdb");
        assertThat(scraper.displayName()).isEqualTo("OMDb");
        assertThat(scraper.supportedMediaTypes()).containsExactlyInAnyOrder(MediaType.MOVIE, MediaType.TV_SHOW);
    }

    @Test
    void needsAKeyAndReportsRejectedKeysLimitsAndErrors() {
        assertThat(scraper(" ").missingConfiguration()).contains(OmdbEnrichmentScraper.MISSING_API_KEY);
        assertThat(scraper(" ").findEnrichment(request(MediaType.MOVIE, "tt0113277"))).isEmpty();
        assertThat(new OmdbEnrichmentScraper().missingConfiguration()).isPresent();
        assertThatThrownBy(() -> new OmdbEnrichmentScraper().findEnrichment(request(MediaType.MOVIE, "tt0113277")))
                .isInstanceOf(IllegalStateException.class);

        answers.put("tt0113277", "{\"Response\": \"False\", \"Error\": \"Invalid API key!\"}");
        assertThatThrownBy(() -> scraper.findEnrichment(request(MediaType.MOVIE, "tt0113277")))
                .isInstanceOfSatisfying(ScraperException.class, e -> assertThat(e.localizedText())
                        .isEqualTo(LocalizedText.of("scraper.apiKeyRejected").with("provider", "OMDb")));
        answers.put("tt0113277", "{\"Response\": \"False\", \"Error\": \"Request limit reached!\"}");
        assertThatThrownBy(() -> scraper.findEnrichment(request(MediaType.MOVIE, "tt0113277")))
                .isInstanceOfSatisfying(ScraperException.class, e -> assertThat(e.localizedText())
                        .isEqualTo(LocalizedText.of("scraper.omdb.dailyLimitReached")));
        answers.put("tt0113277", "no JSON");
        assertThatThrownBy(() -> scraper.findEnrichment(request(MediaType.MOVIE, "tt0113277")))
                .isInstanceOfSatisfying(ScraperException.class, e -> assertThat(e.localizedText())
                        .isEqualTo(LocalizedText.of("scraper.unexpectedResponse").with("provider", "OMDb")));
        answers.remove("tt0113277");
        assertThatThrownBy(() -> scraper.findEnrichment(request(MediaType.MOVIE, "tt0113277")))
                .isInstanceOfSatisfying(ScraperException.class, e -> assertThat(e.localizedText())
                        .isEqualTo(LocalizedText.of("scraper.httpError").with("provider", "OMDb").with("status", 500)));
    }

    @Test
    void reportsAnUnreachableApi() {
        OmdbEnrichmentScraper offline = new OmdbEnrichmentScraper();
        offline.initialize(context(Map.of("api-key", API_KEY, "base-url", "http://127.0.0.1:1")));

        assertThatThrownBy(() -> offline.findEnrichment(request(MediaType.MOVIE, "tt0113277")))
                .isInstanceOfSatisfying(ScraperException.class, e ->
                        assertThat(e.localizedText().key()).isEqualTo("scraper.unreachable"))
                .hasMessageContaining("OMDb cannot be reached");
    }

    private OmdbEnrichmentScraper scraper(String apiKey) {
        OmdbEnrichmentScraper created = new OmdbEnrichmentScraper();
        created.initialize(context(Map.of("api-key", apiKey,
                "base-url", "http://127.0.0.1:" + server.getAddress().getPort() + "/")));
        return created;
    }

    private static ScrapeRequest request(MediaType type, String imdbId) {
        return new ScrapeRequest(type, "Heat", 1995, Map.of("imdb", imdbId));
    }

    private static PluginContext context(Map<String, String> settings) {
        HttpClient httpClient = HttpClient.newHttpClient();
        return new PluginContext() {
            @Override
            public Optional<String> setting(String name) {
                return Optional.ofNullable(settings.get(name));
            }

            @Override
            public HttpClient httpClient() {
                return httpClient;
            }

            @Override
            public String userAgent() {
                return "KMM-Test";
            }
        };
    }

    private void handle(HttpExchange exchange) throws IOException {
        String uri = exchange.getRequestURI().toString();
        requests.add(uri);
        String imdbId = uri.replaceAll(".*[?&]i=([^&]*).*", "$1");
        String answer = answers.get(imdbId);
        byte[] body = (answer == null ? "Fehler" : answer).getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(answer == null ? 500 : 200, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }
}
