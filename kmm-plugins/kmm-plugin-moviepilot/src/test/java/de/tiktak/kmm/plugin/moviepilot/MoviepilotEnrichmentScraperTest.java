package de.tiktak.kmm.plugin.moviepilot;

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
import de.tiktak.kmm.plugin.api.ScrapeRequest;
import de.tiktak.kmm.plugin.api.ScraperException;

class MoviepilotEnrichmentScraperTest {

    private static final String PATE = """
            <html><body><script id="__NEXT_DATA__" type="application/json">
            {"props": {"initialProps": {"pageProps": {
              "imdbId": "tt0068646",
              "description": "<p>Ein Klassiker.</p><p><strong>Handlung von Der Pate</strong><br />\\nDon Corleone (<a href=\\"/people/marlon-brando\\">Marlon Brando</a>) &amp; seine Familie.</p><p>Zweiter Absatz.</p><p><strong>Hintergrund</strong><br />Produktionsnotizen.</p>",
              "listOfLists": {"item": {"rated": 16, "runtime": 175, "premiereDate": null, "cinemaStartDate": "1972-08-24"}},
              "tagGroups": [
                {"title": "Produktionsland", "content": [{"name": "USA"}]},
                {"title": "Altersfreigabe", "content": [{"name": "Ab 16"}]},
                {"title": "Genre", "content": [{"name": "Drama"}, {"name": "Kriminalfilm"}, {"name": "Drama"}]}],
              "crew": [
                {"title": "Gordon Willis", "titleSubline": "Kameramann/frau", "itemprop": null},
                {"title": "Francis Ford Coppola", "titleSubline": "Regisseur/in", "itemprop": "director"},
                {"title": "Mario Puzo", "titleSubline": "Drehbuchautor/in", "itemprop": null}],
              "cast": [{"title": "Marlon Brando", "titleSubline": "Don Vito Corleone", "itemprop": "actor"},
                {"title": "Al Pacino", "titleSubline": "Michael Corleone", "itemprop": "actor"}]
            }}}}
            </script></body></html>""";
    private static final String SPARSE = """
            <script id="__NEXT_DATA__" type="application/json">
            {"props": {"initialProps": {"pageProps": {"imdbId": "tt0036818",
              "description": "<p>Nur Handlung ohne Überschrift.</p>",
              "listOfLists": {"item": {"rated": null, "runtime": 0, "premiereDate": "unbekannt"}}}}}}
            </script>""";

    private HttpServer server;
    private final Map<String, Response> responses = new ConcurrentHashMap<>();
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final Map<String, String> settings = new ConcurrentHashMap<>();
    private MoviepilotEnrichmentScraper scraper;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        settings.put(MoviepilotSettings.BASE_URL, "http://127.0.0.1:%d/".formatted(server.getAddress().getPort()));
        settings.put(MoviepilotSettings.REQUEST_DELAY_MS, "0");
        scraper = new MoviepilotEnrichmentScraper();
        scraper.initialize(new TestContext());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void describesItself() {
        assertThat(scraper.id()).isEqualTo("moviepilot");
        assertThat(scraper.displayName()).isEqualTo("Moviepilot");
        assertThat(scraper.supportedMediaTypes()).containsExactly(MediaType.MOVIE);
        assertThat(scraper.ratingSources()).isEmpty();
        assertThat(scraper.providesDetails()).isTrue();
        assertThat(scraper.order()).isLessThan(100);
    }

    @Test
    void readsTheMoviePageWhenTheImdbIdMatches() {
        responses.put("/movies/der-pate", new Response(200, PATE));

        Enrichment enrichment = scraper.findEnrichment(request("Der Pate", "tt0068646")).orElseThrow();

        assertThat(enrichment.plot()).isEqualTo("Don Corleone (Marlon Brando) & seine Familie.\nZweiter Absatz.");
        assertThat(enrichment.runtime()).isEqualTo(175);
        assertThat(enrichment.premiered()).isEqualTo(LocalDate.of(1972, 8, 24));
        assertThat(enrichment.certification()).isEqualTo("FSK 16");
        assertThat(enrichment.genres()).as("keywords instead of genres").isEmpty();
        assertThat(enrichment.countries()).containsExactly("USA");
        assertThat(enrichment.directors()).containsExactly("Francis Ford Coppola");
        assertThat(enrichment.writers()).containsExactly("Mario Puzo");
        assertThat(enrichment.actors()).containsExactly("Marlon Brando", "Al Pacino");
        assertThat(enrichment.ratings()).isEmpty();
    }

    @Test
    void rejectsAPageOfAnotherMovie() {
        responses.put("/movies/der-pate", new Response(200, PATE));

        assertThat(scraper.findEnrichment(request("Der Pate", "tt9999999"))).isEmpty();
    }

    @Test
    void leavesUnknownValuesEmpty() {
        responses.put("/movies/die-feuerzangenbowle", new Response(200, SPARSE));

        Enrichment enrichment = scraper.findEnrichment(request("Die Feuerzangenbowle", "tt0036818")).orElseThrow();

        assertThat(enrichment.plot()).isEqualTo("Nur Handlung ohne Überschrift.");
        assertThat(enrichment.runtime()).isNull();
        assertThat(enrichment.premiered()).isNull();
        assertThat(enrichment.certification()).isNull();
        assertThat(enrichment.countries()).isEmpty();
    }

    @Test
    void asksNothingWithoutAUsableIdOrForShows() {
        assertThat(scraper.findEnrichment(new ScrapeRequest(MediaType.MOVIE, "Der Pate", null, Map.of()))).isEmpty();
        assertThat(scraper.findEnrichment(request("Der Pate", "kaputt"))).isEmpty();
        assertThat(scraper.findEnrichment(request("???", "tt0068646"))).isEmpty();
        assertThat(scraper.findEnrichment(new ScrapeRequest(MediaType.TV_SHOW, "ALF", null,
                Map.of("imdb", "tt0090390")))).isEmpty();

        assertThat(requests).isEmpty();
    }

    @Test
    void treatsAMissingPageAsNotFound() {
        assertThat(scraper.findEnrichment(request("Unbekannt", "tt0068646"))).isEmpty();
        assertThat(requests).containsExactly("/movies/unbekannt");
    }

    @Test
    void reportsBlockedRequestsAndChangedPages() {
        responses.put("/movies/gesperrt", new Response(403, ""));
        responses.put("/movies/umgebaut", new Response(200, "<html>neu</html>"));
        responses.put("/movies/kaputt", new Response(200, "<script id=\"__NEXT_DATA__\">{kein json</script>"));

        assertThatThrownBy(() -> scraper.findEnrichment(request("Gesperrt", "tt0068646")))
                .isInstanceOfSatisfying(ScraperException.class, e -> assertThat(e.localizedText())
                        .isEqualTo(LocalizedText.of("scraper.httpError").with("provider", "Moviepilot")
                                .with("status", 403)));
        assertThatThrownBy(() -> scraper.findEnrichment(request("Umgebaut", "tt0068646")))
                .isInstanceOfSatisfying(ScraperException.class, e -> assertThat(e.localizedText())
                        .isEqualTo(LocalizedText.of("scraper.moviepilot.unknownPageFormat")));
        assertThatThrownBy(() -> scraper.findEnrichment(request("Kaputt", "tt0068646")))
                .isInstanceOfSatisfying(ScraperException.class, e -> assertThat(e.localizedText())
                        .isEqualTo(LocalizedText.of("scraper.moviepilot.unknownPageFormat")));
    }

    @Test
    void reportsAnUnreachableSite() {
        server.stop(0);

        assertThatThrownBy(() -> scraper.findEnrichment(request("Der Pate", "tt0068646")))
                .isInstanceOfSatisfying(ScraperException.class, e ->
                        assertThat(e.localizedText().key()).isEqualTo("scraper.unreachable"))
                .hasMessageContaining("Moviepilot cannot be reached");
    }

    @Test
    void needsInitialization() {
        assertThatThrownBy(() -> new MoviepilotEnrichmentScraper().findEnrichment(request("Heat", "tt0113277")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void derivesTheAddressFromTheTitle() {
        assertThat(MoviepilotEnrichmentScraper.slug("2001: Odyssee im Weltraum")).isEqualTo("2001-odyssee-im-weltraum");
        assertThat(MoviepilotEnrichmentScraper.slug("Die Brücke am Kwai")).isEqualTo("die-bruecke-am-kwai");
        assertThat(MoviepilotEnrichmentScraper.slug("Léon – Der Profi")).isEqualTo("leon-der-profi");
        assertThat(MoviepilotEnrichmentScraper.slug("Heiß & Kalt!")).isEqualTo("heiss-kalt");
    }

    private static ScrapeRequest request(String title, String imdbId) {
        return new ScrapeRequest(MediaType.MOVIE, title, null, Map.of("imdb", imdbId));
    }

    private void handle(HttpExchange exchange) throws IOException {
        requests.add(exchange.getRequestURI().getRawPath());
        Response response = responses.getOrDefault(exchange.getRequestURI().getRawPath(), new Response(404, ""));
        byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(response.status(), body.length == 0 ? -1 : body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private record Response(int status, String body) {
    }

    private final class TestContext implements PluginContext {

        @Override
        public Optional<String> setting(String name) {
            return Optional.ofNullable(settings.get(name));
        }

        @Override
        public HttpClient httpClient() {
            return HttpClient.newHttpClient();
        }

        @Override
        public String userAgent() {
            return "KMM-Test";
        }
    }
}
