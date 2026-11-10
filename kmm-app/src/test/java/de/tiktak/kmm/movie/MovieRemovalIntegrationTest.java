package de.tiktak.kmm.movie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
 * Removing movies end to end via REST and job, from the library only and with the files.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser
class MovieRemovalIntegrationTest {

    private static final String JSON = "application/json";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcClient jdbcClient;

    @TempDir
    Path library;

    private long sourceId;

    @BeforeEach
    void createLibrary() throws IOException {
        file("Heat (1995)/Heat.mkv");
        Files.writeString(library.resolve("Heat (1995)/Heat.nfo"), """
                <movie><title>Heat</title><year>1995</year><genre>Krimi</genre>
                <actor><name>Al Pacino</name></actor></movie>""");
        file("Heat (1995)/Heat-poster.jpg");
        file("Sammlung/Ronin.mkv");
        file("Sammlung/Alien.mkv");
        file("Sammlung/Alien.nfo");
        file("Brazil/VIDEO_TS/VIDEO_TS.IFO");
        file("Brazil/VIDEO_TS/VTS_01_1.VOB");
        sourceId = createSource();
        scan();
    }

    @AfterEach
    void deleteLibrary() {
        jdbcClient.sql("DELETE FROM media_source").update();
    }

    @Test
    void removesAMovieFromTheLibraryOnlyAndTheNextScanFindsItAgain() {
        long heatId = movieId("Heat");

        JsonNode result = runJob(mockMvc.post().uri(MovieRemovalController.MOVIE_REMOVAL_PATH, heatId).with(csrf())
                .contentType(JSON).content("{\"deleteFiles\": false}").exchange());

        assertThat(result.get("removedEntries").asInt()).isEqualTo(1);
        assertThat(library.resolve("Heat (1995)/Heat.mkv")).exists();
        assertThat(movieCount()).isEqualTo(3);
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM movie_credit").query(Integer.class).single()).isZero();

        scan();

        assertThat(movieCount()).isEqualTo(4);
    }

    @Test
    void deletesAMovieWithItsFolder() {
        long heatId = movieId("Heat");

        JsonNode plan = read(mockMvc.get().uri(MovieRemovalController.MOVIE_REMOVAL_PATH, heatId).exchange());
        assertThat(plan.get("files")).hasSize(3);
        assertThat(plan.get("directories").get(0).asString()).isEqualTo(library.resolve("Heat (1995)").toString());

        JsonNode result = runJob(mockMvc.post().uri(MovieRemovalController.MOVIE_REMOVAL_PATH, heatId).with(csrf())
                .contentType(JSON).content("{\"deleteFiles\": true}").exchange());

        assertThat(result.get("deletedFiles").asInt()).isEqualTo(3);
        assertThat(library.resolve("Heat (1995)")).doesNotExist();
        assertThat(library).exists();
        assertThat(movieCount()).isEqualTo(3);
    }

    @Test
    void deletesASelectionAndLeavesOtherMoviesInTheSameFolderAlone() {
        long alienId = movieId("Alien");
        long brazilId = movieId("Brazil");
        String selection = "{\"movieIds\": [%d, %d], \"deleteFiles\": true}".formatted(alienId, brazilId);

        JsonNode plan = read(mockMvc.post().uri(MovieRemovalController.SELECTION_PLAN_PATH).with(csrf())
                .contentType(JSON).content(selection).exchange());
        assertThat(plan.get("entries")).hasSize(2);
        assertThat(plan.get("files")).hasSize(4);

        JsonNode result = runJob(mockMvc.post().uri(MovieRemovalController.SELECTION_REMOVAL_PATH).with(csrf())
                .contentType(JSON).content(selection).exchange());

        assertThat(result.get("removedEntries").asInt()).isEqualTo(2);
        assertThat(library.resolve("Sammlung/Alien.mkv")).doesNotExist();
        assertThat(library.resolve("Sammlung/Ronin.mkv")).exists();
        assertThat(library.resolve("Brazil")).doesNotExist();
        assertThat(movieCount()).isEqualTo(2);
    }

    private long createSource() {
        MvcTestResult result = mockMvc.post().uri(MediaSourceController.BASE_PATH).with(csrf()).contentType(JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Filme", "path", library.toString(),
                        "mediaType", "MOVIE")))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return read(result).get("id").asLong();
    }

    private void scan() {
        runJob(mockMvc.post().uri(LibraryScanController.SCAN_PATH, sourceId).with(csrf()).exchange());
        // The technical details of new files are read afterwards; on Windows this locks the files.
        await().atMost(TIMEOUT).pollInSameThread()
                .until(() -> read(mockMvc.get().uri(JobController.BASE_PATH).exchange()).valueStream()
                        .noneMatch(job -> "RUNNING".equals(job.get("status").asString())));
    }

    private JsonNode runJob(MvcTestResult started) {
        assertThat(started).hasStatus(HttpStatus.ACCEPTED);
        String jobId = read(started).get("id").asString();
        JsonNode job = await().atMost(TIMEOUT).pollInSameThread()
                .until(() -> read(mockMvc.get().uri(JobController.BASE_PATH + "/" + jobId).exchange()),
                        node -> !"RUNNING".equals(node.get("status").asString()));
        assertThat(job.get("status").asString()).as(job.toString()).isEqualTo("SUCCEEDED");
        return job.get("result");
    }

    private long movieId(String title) {
        return jdbcClient.sql("SELECT id FROM movie WHERE title = ?").params(title).query(Long.class).single();
    }

    private int movieCount() {
        return jdbcClient.sql("SELECT COUNT(*) FROM movie").query(Integer.class).single();
    }

    private JsonNode read(MvcTestResult result) {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private void file(String relativePath) throws IOException {
        Path file = library.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "x");
    }
}
