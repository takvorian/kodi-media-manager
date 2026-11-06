package de.tiktak.kmm.plugin.ofdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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

class OfdbEnrichmentScraperTest {

    private static final String SITEMAP_INDEX = """
            <sitemapindex>
            <sitemap><loc>https://www.ofdb.de/sitemap/sitemap1.xml</loc></sitemap>
            <sitemap><loc>https://www.ofdb.de/sitemap/sitemap2.xml</loc></sitemap>
            </sitemapindex>""";
    private static final String SITEMAP_1 = """
            <urlset><url><loc>https://www.ofdb.de/film/858,American-History-X/</loc></url>
            <url><loc>https://www.ofdb.de/film/3,Aliens-Die-Rückkehr/</loc></url>
            <url><loc>https://www.ofdb.de/film/70,Heat/</loc></url></urlset>""";
    private static final String SITEMAP_2 = """
            <urlset><url><loc>https://www.ofdb.de/film/71,Heat/</loc></url>
            <url><loc>https://www.ofdb.de/film/90,Tom-&amp;-Jerry/</loc></url></urlset>""";
    private static final String HISTORY_X = """
            <h1 itemprop="name">American History X (1998)</h1>
            <a href="https://www.ofdb.de/liste/genre/Drama/"><span itemprop="genre">Drama</span></a>
            <a href="https://www.ofdb.de/liste/genre/Krimi/"><span itemprop="genre">Krimi</span></a>
            <dd><a href="x"><span itemprop="countryOfOrigin">USA</span></a></dd>
            <span itemprop="director" itemscope itemtype="https://schema.org/Person">
             <a href="https://www.ofdb.de/person/1,Tony-Kaye/"><span itemprop="name">Tony Kaye</span></a></span>
            <span itemprop="actor" itemscope><a href="y"><span itemprop="name">Edward Norton</span></a></span>
            <span itemprop="actor" itemscope><a href="z"><span itemprop="name">Edward Furlong</span></a></span>
            <a href="https://www.ofdb.de/film/858,550260,American-History-X/plot/">Inhalt</a>
            <a href="http://www.imdb.com/title/tt0120586/" target="_blank">IMDb</a>
            <a class="text_link" href="x">Blu-ray: Concorde, Freigabe: FSK 16</a>
            <a class="text_link" href="y">DVD: Concorde, Freigabe: FSK 16</a>""";
    private static final String PLOT = """
            <h5 class="flagline-lg">Inhaltsangabe</h5>
            <p class="flagline_author">von <a href="x">ulukai</a></p>
            <h4 class="mb15">Derek sitzt wegen des ...</h4>
            <p>... Mordes im Gef&auml;ngnis.</p>""";

    private HttpServer server;
    private final Map<String, Response> responses = new ConcurrentHashMap<>();
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final Map<String, String> settings = new ConcurrentHashMap<>();
    private OfdbEnrichmentScraper scraper;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        settings.put(OfdbSettings.BASE_URL, "http://127.0.0.1:%d/".formatted(server.getAddress().getPort()));
        settings.put(OfdbSettings.REQUEST_DELAY_MS, "0");
        scraper = new OfdbEnrichmentScraper();
        scraper.initialize(new TestContext());
        page("/sitemap/sitemap_index.xml", SITEMAP_INDEX);
        page("/sitemap/sitemap1.xml", SITEMAP_1);
        page("/sitemap/sitemap2.xml", SITEMAP_2);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void describesItself() {
        assertThat(scraper.id()).isEqualTo("ofdb");
        assertThat(scraper.displayName()).isEqualTo("OFDb");
        assertThat(scraper.supportedMediaTypes()).containsExactly(MediaType.MOVIE);
        assertThat(scraper.ratingSources()).isEmpty();
        assertThat(scraper.providesDetails()).isTrue();
        assertThat(scraper.order()).isLessThan(100);
    }

    @Test
    void readsTheFilmFoundOverTheSitemapWhenTheImdbIdMatches() {
        page("/film/858,x/", HISTORY_X);
        page("/film/858,550260,x/plot/", PLOT);

        Enrichment enrichment = scraper.findEnrichment(request("American History X", "tt0120586")).orElseThrow();

        assertThat(enrichment.plot()).isEqualTo("Derek sitzt wegen des Mordes im Gefängnis.");
        assertThat(enrichment.certification()).isEqualTo("FSK 16");
        assertThat(enrichment.genres()).containsExactly("Drama", "Krimi");
        assertThat(enrichment.countries()).containsExactly("USA");
        assertThat(enrichment.directors()).containsExactly("Tony Kaye");
        assertThat(enrichment.actors()).containsExactly("Edward Norton", "Edward Furlong");
        assertThat(enrichment.writers()).isEmpty();
        assertThat(enrichment.ratings()).isEmpty();
    }

    @Test
    void checksSameNamedFilmsUntilTheImdbIdMatches() {
        page("/film/70,x/", "<a href=\"http://www.imdb.com/title/tt0000070/\">IMDb</a>");
        page("/film/71,x/", "<a href=\"http://www.imdb.com/title/tt0113277/\">IMDb</a>"
                + "<span itemprop=\"genre\">Krimi</span>");

        Enrichment enrichment = scraper.findEnrichment(request("Heat", "tt0113277")).orElseThrow();

        assertThat(enrichment.genres()).containsExactly("Krimi");
        assertThat(enrichment.plot()).isNull();
        assertThat(enrichment.certification()).isNull();
        assertThat(requests).contains("/film/70,x/", "/film/71,x/");
    }

    @Test
    void findsNothingWhenNoFilmMatches() {
        page("/film/70,x/", "<a href=\"http://www.imdb.com/title/tt0000070/\">IMDb</a>");
        page("/film/71,x/", "keine ID");

        assertThat(scraper.findEnrichment(request("Heat", "tt0113277"))).isEmpty();
        assertThat(scraper.findEnrichment(request("Unbekannter Film", "tt0113277"))).isEmpty();
    }

    @Test
    void matchesTitlesWithArticleAndSpecialCharacters() {
        page("/film/3,x/", "<a href=\"http://www.imdb.com/title/tt0090605/\">IMDb</a>");
        page("/film/90,x/", "<a href=\"http://www.imdb.com/title/tt1361336/\">IMDb</a>");

        assertThat(scraper.findEnrichment(request("Aliens - Die Rückkehr", "tt0090605"))).isPresent();
        assertThat(scraper.findEnrichment(request("Tom & Jerry", "tt1361336"))).isPresent();
    }

    @Test
    void loadsTheSitemapsOnlyOnce() {
        scraper.findEnrichment(request("Unbekannt", "tt0113277"));
        scraper.findEnrichment(request("Unbekannt", "tt0113277"));

        assertThat(requests.stream().filter(path -> path.startsWith("/sitemap/"))).hasSize(3);
    }

    @Test
    void reloadsAnOutdatedIndex() {
        OfdbFilmIndex index = new OfdbFilmIndex(new OfdbClient(HttpClient.newHttpClient(),
                OfdbSettings.from(new TestContext()), "KMM-Test"), Duration.ZERO);

        assertThat(index.candidates("Heat")).containsExactly(70, 71);
        index.candidates("Heat");

        assertThat(requests.stream().filter(path -> path.equals("/sitemap/sitemap_index.xml"))).hasSize(2);
    }

    @Test
    void limitsSameNamedCandidates() {
        StringBuilder many = new StringBuilder();
        for (int id = 100; id < 110; id++) {
            many.append("<loc>https://www.ofdb.de/film/%d,Solo/</loc>".formatted(id));
        }
        page("/sitemap/sitemap2.xml", many.toString());
        OfdbFilmIndex index = new OfdbFilmIndex(new OfdbClient(HttpClient.newHttpClient(),
                OfdbSettings.from(new TestContext()), "KMM-Test"), OfdbFilmIndex.MAX_AGE);

        assertThat(index.candidates("Solo")).hasSize(OfdbFilmIndex.MAX_CANDIDATES);
    }

    @Test
    void asksNothingWithoutAUsableIdOrForShows() {
        assertThat(scraper.findEnrichment(new ScrapeRequest(MediaType.MOVIE, "Heat", null, Map.of()))).isEmpty();
        assertThat(scraper.findEnrichment(request("Heat", "kaputt"))).isEmpty();
        assertThat(scraper.findEnrichment(new ScrapeRequest(MediaType.TV_SHOW, "ALF", null,
                Map.of("imdb", "tt0090390")))).isEmpty();
        assertThat(requests).isEmpty();

        assertThat(scraper.findEnrichment(request("???", "tt0113277"))).isEmpty();
        assertThat(requests).isEmpty();
    }

    @Test
    void reportsMissingOrEmptySitemapsAndErrors() {
        responses.remove("/sitemap/sitemap_index.xml");
        assertThatThrownBy(() -> scraper.findEnrichment(request("Heat", "tt0113277")))
                .isInstanceOfSatisfying(ScraperException.class, e -> assertThat(e.localizedText())
                        .isEqualTo(LocalizedText.of("scraper.ofdb.sitemapMissing")
                                .with("path", "/sitemap/sitemap_index.xml")));

        page("/sitemap/sitemap_index.xml", "<sitemapindex></sitemapindex>");
        assertThatThrownBy(() -> scraper.findEnrichment(request("Heat", "tt0113277")))
                .isInstanceOfSatisfying(ScraperException.class, e -> assertThat(e.localizedText())
                        .isEqualTo(LocalizedText.of("scraper.ofdb.noMovies")));

        responses.put("/sitemap/sitemap_index.xml", new Response(403, ""));
        assertThatThrownBy(() -> scraper.findEnrichment(request("Heat", "tt0113277")))
                .isInstanceOfSatisfying(ScraperException.class, e -> assertThat(e.localizedText())
                        .isEqualTo(LocalizedText.of("scraper.httpError").with("provider", "OFDb").with("status", 403)));
    }

    @Test
    void reportsAnUnreachableSite() {
        server.stop(0);

        assertThatThrownBy(() -> scraper.findEnrichment(request("Heat", "tt0113277")))
                .isInstanceOfSatisfying(ScraperException.class, e ->
                        assertThat(e.localizedText().key()).isEqualTo("scraper.unreachable"))
                .hasMessageContaining("OFDb cannot be reached");
    }

    @Test
    void needsInitialization() {
        assertThatThrownBy(() -> new OfdbEnrichmentScraper().findEnrichment(request("Heat", "tt0113277")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void readsTheCertificationOnlyWhenAllVersionsAgree() {
        assertThat(OfdbPage.certification("Freigabe: FSK Keine Jugendfreigabe/ab 18\" x Freigabe: FSK 18<"))
                .isEqualTo("FSK 18");
        assertThat(OfdbPage.certification("Freigabe: FSK o.Al.<")).isEqualTo("FSK 0");
        assertThat(OfdbPage.certification("Freigabe: FSK 16< Freigabe: FSK 18<")).isNull();
        assertThat(OfdbPage.certification("Freigabe: FSK ungeprüft<")).isNull();
        assertThat(OfdbPage.certification("nichts")).isNull();
    }

    @Test
    void readsPlotsWithoutASplitSentence() {
        assertThat(OfdbPage.plot("""
                <h5 class="flagline-lg">Inhaltsangabe</h5><h4 class="mb15">Ein Titel.</h4><p>Ein Text.</p>"""))
                .isEqualTo("Ein Titel. Ein Text.");
        assertThat(OfdbPage.plot("""
                <h5 class="flagline-lg">Inhaltsangabe</h5><h4 class="mb15"></h4><p></p>""")).isNull();
        assertThat(OfdbPage.plot("keine Angabe")).isNull();
    }

    private void page(String path, String body) {
        responses.put(path, new Response(200, body));
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
