package de.tiktak.kmm.movie;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import de.tiktak.kmm.plugin.ScraperController;
import de.tiktak.kmm.scanner.DiscFormat;

/**
 * Loads the real TMDB plugin JAR via PF4J and addresses it through the REST API. This also verifies
 * that the plugin receives Jackson and the plugin API from the application at runtime. TMDB itself
 * is replaced by a local HTTP server.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser
@DirtiesContext
class TmdbPluginIntegrationTest {

    private static final String TMDB_PLUGIN_JAR = "kmm-plugin-tmdb.jar";
    private static final String SEARCH_RESPONSE = """
            {"results": [{"id": 949, "title": "Heat", "original_title": "Heat", "release_date": "1995-12-15",
                          "overview": "Ein Meisterdieb und ein Polizist.", "poster_path": "/heat.jpg"}]}
            """;
    private static final String MOVIE_RESPONSE = """
            {"id": 949, "imdb_id": "tt0113277", "title": "Heat", "original_title": "Heat",
             "release_date": "1995-12-15", "runtime": 170, "overview": "Neil McCauley plant einen Coup.",
             "genres": [{"name": "Krimi"}],
             "release_dates": {"results": [{"iso_3166_1": "DE", "release_dates": [{"certification": "16"}]}]}}
            """;
    private static final String VIDEOS_RESPONSE = """
            {"id": 949, "results": [
              {"iso_639_1": "en", "name": "Heat Trailer", "key": "enTrailer01", "site": "YouTube", "size": 1080,
               "type": "Trailer", "official": true, "published_at": "2015-06-01T10:00:00.000Z"},
              {"iso_639_1": "de", "name": "Heat Trailer Deutsch", "key": "deTrailer01", "site": "YouTube", "size": 720,
               "type": "Trailer", "official": true, "published_at": "2015-06-01T10:00:00.000Z"},
              {"iso_639_1": "de", "name": "Woanders", "key": "x", "site": "Vimeo", "size": 1080,
               "type": "Trailer", "official": true}]}
            """;

    @TempDir
    static Path pluginDirectory;

    private static HttpServer tmdb;

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private MovieRepository movieRepository;

    @Autowired
    private JdbcClient jdbcClient;

    private long movieId;

    @DynamicPropertySource
    static void tmdbPlugin(DynamicPropertyRegistry registry) throws IOException {
        Path builtPlugin = Path.of(System.getProperty("kmm.test-plugins.directory"), TMDB_PLUGIN_JAR);
        Files.copy(builtPlugin, pluginDirectory.resolve(TMDB_PLUGIN_JAR));
        tmdb = startFakeTmdb();
        registry.add("kmm.plugins.directory", pluginDirectory::toString);
        registry.add("kmm.plugins.settings.tmdb.api-key", () -> "0123456789abcdef0123456789abcdef");
        registry.add("kmm.plugins.settings.tmdb.base-url",
                () -> "http://127.0.0.1:" + tmdb.getAddress().getPort() + "/3");
    }

    @AfterAll
    static void stopFakeTmdb() {
        tmdb.stop(0);
    }

    @BeforeEach
    void createMovie() {
        jdbcClient.sql("""
                INSERT INTO media_source (name, path, media_type, created_at)
                VALUES ('Filme', '/media/filme', 'MOVIE', CURRENT_TIMESTAMP)
                """).update();
        long sourceId = jdbcClient.sql("SELECT id FROM media_source WHERE name = 'Filme'").query(Long.class).single();
        movieId = movieRepository.save(
                new Movie(sourceId, "/media/filme/Heat.mkv", "Heat", 1995, DiscFormat.FILE, 1)).getId();
    }

    @AfterEach
    void deleteLibrary() {
        jdbcClient.sql("DELETE FROM media_source").update();
    }

    @Test
    void listsTheTmdbPluginAsReadyScraper() {
        assertThat(mockMvc.get().uri(ScraperController.METADATA_SCRAPERS_PATH))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        [{"id": "tmdb", "mediaTypes": ["MOVIE"], "missingConfiguration": null}]
                        """);
    }

    @Test
    void listsTheTmdbPluginAsTvShowScraper() {
        assertThat(mockMvc.get().uri(ScraperController.TV_SHOW_SCRAPERS_PATH))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        [{"id": "tmdb", "mediaTypes": ["TV_SHOW"], "missingConfiguration": null, "attribution": null}]
                        """);
    }

    @Test
    void searchesAndProposesMetadataThroughThePlugin() {
        assertThat(mockMvc.get().uri(MovieScrapeController.SEARCH_PATH, movieId).param("scraper", "tmdb"))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        [{"providerId": "949", "title": "Heat", "year": 1995,
                          "posterUrl": "https://image.tmdb.org/t/p/w185/heat.jpg"}]
                        """);

        assertThat(mockMvc.get().uri(MovieScrapeController.PROPOSAL_PATH, movieId)
                .param("scraper", "tmdb").param("providerId", "949"))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"metadata": {"title": "Heat", "year": 1995, "premiered": "1995-12-15", "runtime": 170,
                         "mpaa": "FSK 16", "genres": ["Krimi"], "imdbId": "tt0113277", "tmdbId": "949"},
                         "collection": null}
                        """);
    }

    @Test
    void proposesYouTubeTrailersThroughThePlugin() {
        jdbcClient.sql("UPDATE movie SET tmdb_id = '949' WHERE id = :id").param("id", movieId).update();

        assertThat(mockMvc.get().uri(MovieTrailerController.CANDIDATES_PATH, movieId))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"candidates": [
                          {"name": "Heat Trailer Deutsch", "language": "de",
                           "url": "plugin://plugin.video.youtube/?action=play_video&videoid=deTrailer01"},
                          {"name": "Heat Trailer", "language": "en"}],
                         "warnings": []}
                        """);
    }

    private static HttpServer startFakeTmdb() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        Map<String, String> responses = Map.of("/3/search/movie", SEARCH_RESPONSE, "/3/movie/949", MOVIE_RESPONSE,
                "/3/movie/949/videos", VIDEOS_RESPONSE);
        server.createContext("/", exchange -> respond(exchange, responses.get(exchange.getRequestURI().getPath())));
        server.start();
        return server;
    }

    private static void respond(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = (body == null ? "{}" : body).getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(body == null ? 404 : 200, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
