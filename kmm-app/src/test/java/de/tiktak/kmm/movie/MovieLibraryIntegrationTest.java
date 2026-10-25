package de.tiktak.kmm.movie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

import de.tiktak.kmm.source.MediaSourceController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Durchläuft den kompletten Weg über die REST-API: Quelle anlegen, scannen, Filme abfragen,
 * erneut scannen und Quelle löschen.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser
@Transactional
class MovieLibraryIntegrationTest {

    private static final String JSON = "application/json";

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MovieRepository movieRepository;

    @TempDir
    Path library;

    @Test
    void scansSourceAndKeepsLibraryInSync() throws IOException {
        Path heat = createFile("Heat (1995)/Heat (1995).mkv");
        createFile("The.Matrix.1999.1080p.BluRay.x264.mp4");
        createFile("Heat (1995)/Heat (1995).nfo");
        long sourceId = createSource();

        assertThat(scan(sourceId)).bodyJson().isLenientlyEqualTo("""
                {"added": 2, "removed": 0, "unchanged": 0}
                """);
        assertThat(mockMvc.get().uri(MovieController.MOVIES_PATH))
                .bodyJson().isLenientlyEqualTo("""
                        {"totalElements": 2, "content": [{"title": "Heat", "year": 1995}, {"title": "The Matrix", "year": 1999}]}
                        """);
        assertThat(mockMvc.get().uri(MovieController.MOVIES_PATH).param("search", "matrix"))
                .bodyJson().extractingPath("$.totalElements").isEqualTo(1);

        Files.delete(heat);
        createFile("Ronin (1998).mkv");
        assertThat(scan(sourceId)).bodyJson().isLenientlyEqualTo("""
                {"added": 1, "removed": 1, "unchanged": 1}
                """);
        assertThat(mockMvc.get().uri(MediaSourceController.BASE_PATH))
                .bodyJson().extractingPath("$[0].lastScannedAt").isNotNull();

        assertThat(mockMvc.delete().uri(MediaSourceController.BASE_PATH + "/" + sourceId).with(csrf())).hasStatus(204);
        movieRepository.flush();
        assertThat(movieRepository.count()).isZero();
    }

    @Test
    void rejectsScanOfUnknownSource() {
        assertThat(mockMvc.post().uri(MovieController.SCAN_PATH, 999L).with(csrf())).hasStatus(404);
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

    private MvcTestResult scan(long sourceId) {
        MvcTestResult result = mockMvc.post().uri(MovieController.SCAN_PATH, sourceId).with(csrf()).exchange();
        assertThat(result).hasStatusOk();
        return result;
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
