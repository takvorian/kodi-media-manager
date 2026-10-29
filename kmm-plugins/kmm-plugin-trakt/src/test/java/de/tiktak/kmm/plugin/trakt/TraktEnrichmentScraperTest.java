package de.tiktak.kmm.plugin.trakt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
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

import de.tiktak.kmm.plugin.api.Enrichment;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.PluginContext;
import de.tiktak.kmm.plugin.api.ProviderRating;
import de.tiktak.kmm.plugin.api.ScrapeRequest;
import de.tiktak.kmm.plugin.api.ScraperException;

class TraktEnrichmentScraperTest {

    private static final String CLIENT_ID = "0123456789abcdef";
    private static final String HEAT_RATINGS = """
            {"rating": 8.23456, "votes": 41234, "distribution": {"10": 9000}}""";

    private final Map<String, Answer> answers = new ConcurrentHashMap<>();
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private TraktEnrichmentScraper scraper;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        answers.put("/movies/tt0113277/ratings", new Answer(200, HEAT_RATINGS));
        answers.put("/search/tmdb/949?type=movie", new Answer(200, """
                [{"type": "movie", "movie": {"title": "Heat", "ids": {"trakt": 1203, "slug": "heat-1995"}}}]"""));
        answers.put("/movies/1203/ratings", new Answer(200, HEAT_RATINGS));
        answers.put("/search/tvdb/78020?type=show", new Answer(200, "[]"));
        answers.put("/search/tmdb/4658?type=show", new Answer(200, """
                [{"type": "show", "show": {"title": "ALF", "ids": {"trakt": 1414}}}]"""));
        answers.put("/shows/1414/ratings", new Answer(200, "{\"rating\": 7.4, \"votes\": 9400}"));
        answers.put("/movies/tt0000001/ratings", new Answer(200, "{\"rating\": 0, \"votes\": 0}"));
        scraper = scraper(CLIENT_ID);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void findsTheMovieRatingByImdbIdWithTheClientId() {
        assertThat(scraper.findEnrichment(request(MediaType.MOVIE, Map.of("imdb", "tt0113277", "tmdb", "949"))))
                .contains(Enrichment.ofRatings(List.of(new ProviderRating("trakt", 8.2, 10, 41234))));
        assertThat(requests).singleElement().satisfies(request -> {
            assertThat(request.apiKey()).isEqualTo(CLIENT_ID);
            assertThat(request.apiVersion()).isEqualTo("2");
        });
    }

    @Test
    void looksUpTmdbAndTvdbIdsWhenThereIsNoImdbId() {
        assertThat(scraper.findEnrichment(request(MediaType.MOVIE, Map.of("tmdb", "949")))).isPresent();
        assertThat(scraper.findEnrichment(request(MediaType.TV_SHOW, Map.of("tvdb", "78020", "tmdb", "4658"))))
                .contains(Enrichment.ofRatings(List.of(new ProviderRating("trakt", 7.4, 10, 9400))));
        assertThat(requests).extracting(Request::path).containsExactly("/search/tmdb/949?type=movie",
                "/movies/1203/ratings", "/search/tvdb/78020?type=show", "/search/tmdb/4658?type=show",
                "/shows/1414/ratings");
    }

    @Test
    void returnsNothingForUnknownUnratedOrInvalidEntries() {
        assertThat(scraper.findEnrichment(request(MediaType.MOVIE, Map.of("imdb", "tt0000001")))).isEmpty();
        assertThat(scraper.findEnrichment(request(MediaType.MOVIE, Map.of("imdb", "tt9999999")))).isEmpty();
        assertThat(scraper.findEnrichment(request(MediaType.MOVIE, Map.of("imdb", "../x", "tmdb", "1;2")))).isEmpty();
        assertThat(scraper.findEnrichment(request(MediaType.TV_EPISODE, Map.of("imdb", "tt0113277")))).isEmpty();
        assertThat(scraper.findEnrichment(request(MediaType.TV_SHOW, Map.of()))).isEmpty();
        assertThat(scraper.supportedMediaTypes()).containsExactlyInAnyOrder(MediaType.MOVIE, MediaType.TV_SHOW);
        assertThat(scraper.id()).isEqualTo("trakt");
        assertThat(scraper.displayName()).isEqualTo("Trakt");
        assertThat(scraper.ratingSources()).containsExactly("trakt");
    }

    @Test
    void needsAClientIdAndReportsRejectedOnes() {
        TraktEnrichmentScraper unconfigured = scraper(" ");
        assertThat(unconfigured.missingConfiguration()).contains(TraktEnrichmentScraper.MISSING_CLIENT_ID);
        assertThat(unconfigured.findEnrichment(request(MediaType.MOVIE, Map.of("imdb", "tt0113277")))).isEmpty();
        assertThat(new TraktEnrichmentScraper().missingConfiguration()).isPresent();
        assertThatThrownBy(() -> new TraktEnrichmentScraper().findEnrichment(request(MediaType.MOVIE, Map.of())))
                .isInstanceOf(IllegalStateException.class);

        answers.put("/movies/tt0113277/ratings", new Answer(401, "{}"));
        assertThatThrownBy(() -> scraper.findEnrichment(request(MediaType.MOVIE, Map.of("imdb", "tt0113277"))))
                .isInstanceOf(ScraperException.class).hasMessageContaining("Client-ID");
        answers.put("/movies/tt0113277/ratings", new Answer(503, "{}"));
        assertThatThrownBy(() -> scraper.findEnrichment(request(MediaType.MOVIE, Map.of("imdb", "tt0113277"))))
                .isInstanceOf(ScraperException.class).hasMessageContaining("503");
        answers.put("/movies/tt0113277/ratings", new Answer(200, "kein JSON"));
        assertThatThrownBy(() -> scraper.findEnrichment(request(MediaType.MOVIE, Map.of("imdb", "tt0113277"))))
                .isInstanceOf(ScraperException.class).hasMessageContaining("unerwartete");
    }

    @Test
    void reportsAnUnreachableApi() {
        TraktEnrichmentScraper offline = new TraktEnrichmentScraper();
        offline.initialize(context(Map.of("client-id", CLIENT_ID, "base-url", "http://127.0.0.1:1")));

        assertThatThrownBy(() -> offline.findEnrichment(request(MediaType.MOVIE, Map.of("imdb", "tt0113277"))))
                .isInstanceOf(ScraperException.class).hasMessageContaining("nicht erreichbar");
    }

    private TraktEnrichmentScraper scraper(String clientId) {
        TraktEnrichmentScraper created = new TraktEnrichmentScraper();
        created.initialize(context(Map.of("client-id", clientId,
                "base-url", "http://127.0.0.1:" + server.getAddress().getPort() + "/")));
        return created;
    }

    private static ScrapeRequest request(MediaType type, Map<String, String> ids) {
        return new ScrapeRequest(type, "Heat", 1995, ids);
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
        requests.add(new Request(uri, exchange.getRequestHeaders().getFirst("trakt-api-key"),
                exchange.getRequestHeaders().getFirst("trakt-api-version")));
        Answer answer = answers.getOrDefault(uri, new Answer(404, "{}"));
        byte[] body = answer.body().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(answer.status(), body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private record Answer(int status, String body) {
    }

    private record Request(String path, String apiKey, String apiVersion) {
    }
}
