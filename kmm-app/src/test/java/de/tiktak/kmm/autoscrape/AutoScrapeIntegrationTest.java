package de.tiktak.kmm.autoscrape;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import de.tiktak.kmm.job.JobController;
import de.tiktak.kmm.library.LibraryScanController;
import de.tiktak.kmm.mediainfo.StreamDetailsService;
import de.tiktak.kmm.movie.MovieController;
import de.tiktak.kmm.source.MediaSourceController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Automatisches Scrapen einer Quelle über die REST-API mit den echten Plugins von TMDB und fanart.tv.
 * Beide Dienste und die Bilddateien liefert ein lokaler HTTP-Server.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser
@DirtiesContext
class AutoScrapeIntegrationTest {

    private static final Duration JOB_TIMEOUT = Duration.ofSeconds(20);
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, 7};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};
    private static final byte[] EXISTING_POSTER = {(byte) 0xFF, (byte) 0xD8, 42};

    @TempDir
    static Path pluginDirectory;

    @TempDir
    Path library;

    private static HttpServer server;

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcClient jdbcClient;

    @MockitoBean
    private StreamDetailsService streamDetails;

    @DynamicPropertySource
    static void plugins(DynamicPropertyRegistry registry) throws IOException {
        Path builtPlugins = Path.of(System.getProperty("kmm.test-plugins.directory"));
        for (String plugin : List.of("kmm-plugin-tmdb.jar", "kmm-plugin-fanarttv.jar")) {
            Files.copy(builtPlugins.resolve(plugin), pluginDirectory.resolve(plugin));
        }
        server = startProviders();
        String base = baseUrl();
        registry.add("kmm.plugins.directory", pluginDirectory::toString);
        registry.add("kmm.plugins.settings.tmdb.api-key", () -> "0123456789abcdef0123456789abcdef");
        registry.add("kmm.plugins.settings.tmdb.base-url", () -> base + "/3");
        registry.add("kmm.plugins.settings.tmdb.image-host", () -> base + "/t/p");
        registry.add("kmm.plugins.settings.fanarttv.api-key", () -> "fanart-key");
        registry.add("kmm.plugins.settings.fanarttv.base-url", () -> base + "/v3");
    }

    @AfterAll
    static void stopProviders() {
        server.stop(0);
    }

    @AfterEach
    void deleteLibrary() {
        jdbcClient.sql("DELETE FROM media_source").update();
    }

    @Test
    void fillsOnlyMissingMetadataAndImages() throws IOException {
        createFile("Heat (1995)/Heat (1995).mkv");
        createFile("Unbekannt (2001)/Unbekannt (2001).mkv");
        createFile("Ronin (1998)/Ronin (1998).mkv");
        Files.writeString(library.resolve("Ronin (1998)/Ronin (1998).nfo"), """
                <movie><title>Ronin (gepflegt)</title><uniqueid type="tmdb">8195</uniqueid></movie>
                """);
        Path roninPoster = Files.write(library.resolve("Ronin (1998)/Ronin (1998)-poster.jpg"), EXISTING_POSTER);
        long sourceId = createSource();
        awaitJob(post(LibraryScanController.SCAN_PATH, sourceId));

        JsonNode result = awaitJob(post(AutoScrapeController.AUTO_SCRAPE_PATH, sourceId));

        assertThat(objectMapper.treeToValue(result, AutoScrapeResult.class)).isEqualTo(new AutoScrapeResult(3, 1, 1, 5, 0,
                0, 0, List.of("Unbekannt"), List.of(), List.of()));
        assertThat(Files.readString(library.resolve("Heat (1995)/Heat (1995).nfo")))
                .contains("<title>Heat</title>", "<runtime>170</runtime>", ">949</uniqueid>");
        assertThat(library.resolve("Heat (1995)/Heat (1995)-poster.jpg")).hasBinaryContent(JPEG);
        assertThat(library.resolve("Heat (1995)/Heat (1995)-fanart.jpg")).exists();
        assertThat(library.resolve("Heat (1995)/Heat (1995)-clearlogo.png")).hasBinaryContent(PNG);
        assertThat(roninPoster).hasBinaryContent(EXISTING_POSTER);
        assertThat(library.resolve("Ronin (1998)/Ronin (1998)-fanart.jpg")).exists();
        assertThat(Files.readString(library.resolve("Ronin (1998)/Ronin (1998).nfo"))).contains("Ronin (gepflegt)");
        assertThat(library.resolve("Unbekannt (2001)/Unbekannt (2001).nfo")).doesNotExist();
    }

    @Test
    void rejectsUnknownSource() {
        assertThat(mockMvc.post().uri(AutoScrapeController.AUTO_SCRAPE_PATH, 999L).with(csrf())).hasStatus(404);
    }

    private long createSource() {
        MvcTestResult result = mockMvc.post().uri(MediaSourceController.BASE_PATH).with(csrf())
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(
                        Map.of("name", "Filme", "path", library.toString(), "mediaType", "MOVIE")))
                .exchange();
        assertThat(result).hasStatus(201);
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("id").asLong();
    }

    private String post(String path, long sourceId) {
        MvcTestResult result = mockMvc.post().uri(path, sourceId).with(csrf()).exchange();
        assertThat(result).hasStatus(202);
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("id").asString();
    }

    private JsonNode awaitJob(String jobId) {
        JsonNode job = await().atMost(JOB_TIMEOUT).pollInSameThread()
                .until(() -> readJob(jobId), node -> !"RUNNING".equals(node.get("status").asString()));
        assertThat(job.get("status").asString()).as(job.toString()).isEqualTo("SUCCEEDED");
        return job.get("result");
    }

    private JsonNode readJob(String jobId) {
        MvcTestResult result = mockMvc.get().uri(JobController.BASE_PATH + "/" + jobId).exchange();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private void createFile(String relativePath) throws IOException {
        Path file = library.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.createFile(file);
    }

    private static String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static HttpServer startProviders() throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String base = "http://127.0.0.1:" + httpServer.getAddress().getPort();
        httpServer.createContext("/3/search/movie", exchange -> {
            boolean heat = exchange.getRequestURI().getRawQuery().contains("query=Heat");
            respond(exchange, "application/json", (heat
                    ? "{\"results\": [{\"id\": 949, \"title\": \"Heat\", \"original_title\": \"Heat\","
                            + " \"release_date\": \"1995-12-15\"}]}"
                    : "{\"results\": []}").getBytes(StandardCharsets.UTF_8));
        });
        json(httpServer, "/3/movie/949", """
                {"id": 949, "imdb_id": "tt0113277", "title": "Heat", "original_title": "Heat",
                 "release_date": "1995-12-15", "runtime": 170, "genres": [{"name": "Krimi"}]}
                """);
        String tmdbImages = """
                {"posters": [{"file_path": "/poster.jpg", "iso_639_1": "de", "vote_average": 5}],
                 "backdrops": [{"file_path": "/backdrop.jpg", "iso_639_1": null, "vote_average": 5}],
                 "logos": []}
                """;
        json(httpServer, "/3/movie/949/images", tmdbImages);
        json(httpServer, "/3/movie/8195/images", tmdbImages);
        String fanart = """
                {"hdmovielogo": [{"id": "1", "url": "%s/fanart/logo.png", "lang": "de", "likes": "3"}]}
                """.formatted(base);
        json(httpServer, "/v3/movies/949", fanart);
        json(httpServer, "/v3/movies/8195", fanart);
        image(httpServer, "/t/p/original/poster.jpg", "image/jpeg", JPEG);
        image(httpServer, "/t/p/original/backdrop.jpg", "image/jpeg", JPEG);
        image(httpServer, "/fanart/logo.png", "image/png", PNG);
        httpServer.start();
        return httpServer;
    }

    private static void json(HttpServer httpServer, String path, String body) {
        image(httpServer, path, "application/json", body.getBytes(StandardCharsets.UTF_8));
    }

    private static void image(HttpServer httpServer, String path, String contentType, byte[] body) {
        httpServer.createContext(path, exchange -> respond(exchange, contentType, body));
    }

    private static void respond(HttpExchange exchange, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }
}
