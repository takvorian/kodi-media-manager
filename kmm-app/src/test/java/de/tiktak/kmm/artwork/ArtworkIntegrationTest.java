package de.tiktak.kmm.artwork;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

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

import com.sun.net.httpserver.HttpServer;

/**
 * Image search and adoption via the REST API with the real plugin JARs of TMDB and fanart.tv.
 * Both services and the image files are served by a local HTTP server.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser
@DirtiesContext
class ArtworkIntegrationTest {

    private static final byte[] TMDB_POSTER = {(byte) 0xFF, (byte) 0xD8, 1};
    private static final byte[] FANART_POSTER = {(byte) 0xFF, (byte) 0xD8, 2};

    @TempDir
    static Path pluginDirectory;

    @TempDir
    Path library;

    private static HttpServer server;

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    private long movieId;

    @DynamicPropertySource
    static void plugins(DynamicPropertyRegistry registry) throws IOException {
        Path builtPlugins = Path.of(System.getProperty("kmm.test-plugins.directory"));
        for (String plugin : List.of("kmm-plugin-tmdb.jar", "kmm-plugin-fanarttv.jar")) {
            Files.copy(builtPlugins.resolve(plugin), pluginDirectory.resolve(plugin));
        }
        server = startServer();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        registry.add("kmm.plugins.directory", pluginDirectory::toString);
        registry.add("kmm.plugins.settings.tmdb.api-key", () -> "0123456789abcdef0123456789abcdef");
        registry.add("kmm.plugins.settings.tmdb.base-url", () -> base + "/3");
        registry.add("kmm.plugins.settings.tmdb.image-host", () -> base + "/t/p");
        registry.add("kmm.plugins.settings.fanarttv.api-key", () -> "fanart-key");
        registry.add("kmm.plugins.settings.fanarttv.base-url", () -> base + "/v3");
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    @BeforeEach
    void createMovie() throws IOException {
        Path movieFile = Files.createFile(library.resolve("Heat.mkv"));
        jdbcClient.sql("INSERT INTO media_source (name, path, media_type, created_at) "
                + "VALUES ('Filme', :path, 'MOVIE', CURRENT_TIMESTAMP)").param("path", library.toString()).update();
        long sourceId = jdbcClient.sql("SELECT id FROM media_source").query(Long.class).single();
        jdbcClient.sql("""
                INSERT INTO movie (source_id, file_path, file_path_hash, title, release_year, disc_format,
                                   part_count, added_at, tmdb_id)
                VALUES (:sourceId, :path, :hash, 'Heat', 1995, 'FILE', 1, CURRENT_TIMESTAMP, '949')
                """)
                .param("sourceId", sourceId)
                .param("path", movieFile.toString())
                .param("hash", sha256(movieFile.toString()))
                .update();
        movieId = jdbcClient.sql("SELECT id FROM movie").query(Long.class).single();
    }

    @AfterEach
    void deleteLibrary() {
        jdbcClient.sql("DELETE FROM media_source").update();
    }

    @Test
    void findsAppliesServesAndRemovesArtwork() {
        assertThat(mockMvc.get().uri(ArtworkController.CANDIDATES_PATH, movieId, "POSTER"))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"candidates": [
                           {"scraperId": "fanarttv", "language": "de", "rating": 3.0},
                           {"scraperId": "tmdb", "language": "de", "rating": 5.4}],
                         "warnings": []}
                        """);

        String tmdbPoster = "http://127.0.0.1:" + server.getAddress().getPort() + "/t/p/original/poster-de.jpg";
        assertThat(mockMvc.put().uri(ArtworkController.TYPE_PATH, movieId, "POSTER").with(csrf())
                .contentType("application/json").content("{\"url\": \"" + tmdbPoster + "\"}"))
                .hasStatusOk();
        assertThat(library.resolve("Heat-poster.jpg")).hasBinaryContent(TMDB_POSTER);

        assertThat(mockMvc.get().uri(ArtworkController.IMAGE_PATH, movieId, "POSTER"))
                .hasStatusOk()
                .hasContentType("image/jpeg");
        assertThat(mockMvc.get().uri(ArtworkController.ARTWORK_PATH, movieId))
                .bodyJson().extractingPath("$[0].exists").isEqualTo(true);

        assertThat(mockMvc.delete().uri(ArtworkController.TYPE_PATH, movieId, "POSTER").with(csrf())).hasStatus(204);
        assertThat(library.resolve("Heat-poster.jpg")).doesNotExist();
    }

    @Test
    void requiresIdForImageSearch() {
        jdbcClient.sql("UPDATE movie SET tmdb_id = NULL").update();

        assertThat(mockMvc.get().uri(ArtworkController.CANDIDATES_PATH, movieId, "POSTER")).hasStatus(422);
    }

    private static HttpServer startServer() throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String base = "http://127.0.0.1:" + httpServer.getAddress().getPort();
        respond(httpServer, "/3/movie/949/images", "application/json", """
                {"posters": [{"file_path": "/poster-de.jpg", "width": 1000, "height": 1500, "iso_639_1": "de",
                              "vote_average": 5.4}],
                 "backdrops": [], "logos": []}
                """.getBytes(StandardCharsets.UTF_8));
        respond(httpServer, "/v3/movies/949", "application/json", """
                {"movieposter": [{"id": "4", "url": "%s/fanart/movies/949/movieposter/heat.jpg", "lang": "de",
                                  "likes": "3"}]}
                """.formatted(base).getBytes(StandardCharsets.UTF_8));
        respond(httpServer, "/t/p/original/poster-de.jpg", "image/jpeg", TMDB_POSTER);
        respond(httpServer, "/fanart/movies/949/movieposter/heat.jpg", "image/jpeg", FANART_POSTER);
        httpServer.start();
        return httpServer;
    }

    private static void respond(HttpServer httpServer, String path, String contentType, byte[] body) {
        httpServer.createContext(path, exchange -> {
            exchange.getResponseHeaders().add("Content-Type", contentType);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
