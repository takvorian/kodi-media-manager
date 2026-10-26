package de.tiktak.kmm.movie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import de.tiktak.kmm.job.JobController;
import de.tiktak.kmm.library.LibraryScanController;
import de.tiktak.kmm.source.MediaSourceController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Durchläuft den kompletten Weg über die REST-API: Quelle anlegen, scannen, Filme abfragen,
 * erneut scannen und Quelle löschen. Der Scan läuft als echter Hintergrund-Job; deshalb arbeitet
 * der Test ohne umschließende Transaktion und räumt die Datenbank danach selbst auf.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser
class MovieLibraryIntegrationTest {

    private static final String JSON = "application/json";
    private static final Duration JOB_TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MovieRepository movieRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @TempDir
    Path library;

    @AfterEach
    void deleteLibrary() {
        // Filme und Genres folgen per ON DELETE CASCADE.
        jdbcClient.sql("DELETE FROM media_source").update();
    }

    @Test
    void scansSourceAndKeepsLibraryInSync() throws IOException {
        Path heat = createFile("Heat (1995)/Heat (1995).mkv");
        createFile("The.Matrix.1999.1080p.BluRay.x264.mp4");
        Files.writeString(library.resolve("Heat (1995)/Heat (1995).nfo"), "<movie><title>kaputt</movie>");
        long sourceId = createSource();

        assertThat(scan(sourceId)).isEqualTo(new ScanResult(sourceId, 2, 0, 0, 0, 1));
        assertThat(mockMvc.get().uri(MovieController.MOVIES_PATH))
                .bodyJson().isLenientlyEqualTo("""
                        {"totalElements": 2, "content": [{"title": "Heat", "year": 1995}, {"title": "The Matrix", "year": 1999}]}
                        """);
        assertThat(mockMvc.get().uri(MovieController.MOVIES_PATH).param("search", "matrix"))
                .bodyJson().extractingPath("$.totalElements").isEqualTo(1);

        Files.delete(heat);
        createFile("Ronin (1998).mkv");
        assertThat(scan(sourceId)).isEqualTo(new ScanResult(sourceId, 1, 0, 1, 1, 0));
        assertThat(mockMvc.get().uri(MediaSourceController.BASE_PATH))
                .bodyJson().extractingPath("$[0].lastScannedAt").isNotNull();

        assertThat(mockMvc.delete().uri(MediaSourceController.BASE_PATH + "/" + sourceId).with(csrf())).hasStatus(204);
        assertThat(movieRepository.count()).isZero();
    }

    @Test
    void readsNfoOnScanAndWritesEditedMetadataBack() throws IOException {
        createFile("Heat (1995)/Heat (1995).mkv");
        Path nfo = library.resolve("Heat (1995)/Heat (1995).nfo");
        Files.writeString(nfo, """
                <movie>
                  <title>Heat – aus der NFO</title>
                  <uniqueid type="imdb" default="true">tt0113277</uniqueid>
                  <actor><name>Al Pacino</name><role>Hanna</role></actor>
                  <set><name>Michael Mann Collection</name></set>
                  <playcount>2</playcount>
                </movie>
                """);
        long sourceId = createSource();
        scan(sourceId);

        long movieId = objectMapper.readTree(mockMvc.get().uri(MovieController.MOVIES_PATH).exchange()
                .getResponse().getContentAsByteArray()).get("content").get(0).get("id").asLong();
        assertThat(mockMvc.get().uri(MovieController.MOVIE_PATH, movieId)).bodyJson().isLenientlyEqualTo("""
                {"metadata": {"title": "Heat – aus der NFO", "year": 1995, "imdbId": "tt0113277"},
                 "supplement": {"actors": [{"name": "Al Pacino", "role": "Hanna"}],
                                "set": {"name": "Michael Mann Collection"}, "playCount": 2},
                 "nfo": {"exists": true}}
                """);

        assertThat(mockMvc.put().uri(MovieController.METADATA_PATH, movieId).with(csrf()).contentType(JSON)
                .content("""
                        {"title": "Heat (Director's Cut)", "year": 1995, "genres": ["Krimi"], "imdbId": "tt0113277",
                         "tmdbId": "949"}
                        """))
                .hasStatusOk();
        String written = Files.readString(nfo);
        assertThat(written).contains("<title>Heat (Director's Cut)</title>", "<genre>Krimi</genre>",
                "<name>Al Pacino</name>", "<role>Hanna</role>", "<name>Michael Mann Collection</name>",
                "<playcount>2</playcount>", "<dateadded>", ">949</uniqueid>");
        assertThat(scan(sourceId).updated()).isZero();

        Files.writeString(nfo, written.replace("Director's Cut", "Extended"));
        Files.setLastModifiedTime(nfo, FileTime.from(Instant.now().plusSeconds(5)));
        assertThat(scan(sourceId).updated()).isEqualTo(1);
        assertThat(mockMvc.get().uri(MovieController.MOVIE_PATH, movieId))
                .bodyJson().extractingPath("$.metadata.title").isEqualTo("Heat (Extended)");
    }

    @Test
    void rejectsScanOfUnknownSource() {
        assertThat(mockMvc.post().uri(LibraryScanController.SCAN_PATH, 999L).with(csrf())).hasStatus(404);
    }

    @Test
    void rejectsOverlappingSource() throws IOException {
        createSource();
        Path nested = Files.createDirectories(library.resolve("Kinder"));

        assertThat(mockMvc.post().uri(MediaSourceController.BASE_PATH).with(csrf()).contentType(JSON)
                .content(sourceJson("Kinder", nested)))
                .hasStatus(409);
    }

    private long createSource() {
        MvcTestResult result = mockMvc.post().uri(MediaSourceController.BASE_PATH).with(csrf()).contentType(JSON)
                .content(sourceJson("Filme", library)).exchange();
        assertThat(result).hasStatus(201);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        return body.get("id").asLong();
    }

    /** Startet den Scan, wartet auf das Ende des Jobs und liefert sein Ergebnis. */
    private ScanResult scan(long sourceId) {
        JsonNode job = startScan(sourceId);
        return objectMapper.treeToValue(awaitResult(job.get("id").asString()), ScanResult.class);
    }

    private JsonNode startScan(long sourceId) {
        MvcTestResult result = mockMvc.post().uri(LibraryScanController.SCAN_PATH, sourceId).with(csrf())
                .exchange();
        assertThat(result).hasStatus(HttpStatus.ACCEPTED);
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private JsonNode awaitResult(String jobId) {
        // Im Test-Thread abfragen, damit der Security-Kontext von @WithMockUser gilt.
        JsonNode job = await().atMost(JOB_TIMEOUT).pollInSameThread()
                .until(() -> readJob(jobId), node -> !"RUNNING".equals(node.get("status").asString()));
        assertThat(job.get("status").asString()).as(job.toString()).isEqualTo("SUCCEEDED");
        return job.get("result");
    }

    private JsonNode readJob(String jobId) {
        MvcTestResult result = mockMvc.get().uri(JobController.BASE_PATH + "/" + jobId).exchange();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private String sourceJson(String name, Path path) {
        return objectMapper.writeValueAsString(
                Map.of("name", name, "path", path.toString(), "mediaType", "MOVIE"));
    }

    private Path createFile(String relativePath) throws IOException {
        Path file = library.resolve(relativePath);
        Files.createDirectories(file.getParent());
        return Files.createFile(file);
    }
}
