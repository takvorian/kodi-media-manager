package de.tiktak.kmm.tag;

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
 * Tag-Manager Ende zu Ende: Tags aus den NFOs zählen, umbenennen, zuweisen, entfernen und löschen; die NFOs
 * tragen danach die neuen Tags.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser
class TagIntegrationTest {

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

    @AfterEach
    void cleanUp() {
        jdbcClient.sql("DELETE FROM media_source").update();
    }

    @Test
    void managesTagsOfMoviesAndShows() throws IOException {
        Path filme = Files.createDirectories(library.resolve("filme"));
        Path heatNfo = givenFile(filme.resolve("Heat/Heat.nfo"), "<movie><title>Heat</title><tag>Krimi</tag>"
                + "<tag>Spencer</tag><actor><name>Al Pacino</name></actor></movie>");
        givenFile(filme.resolve("Heat/Heat.mkv"), "");
        Path roninNfo = givenFile(filme.resolve("Ronin/Ronin.nfo"), "<movie><title>Ronin</title></movie>");
        givenFile(filme.resolve("Ronin/Ronin.mkv"), "");
        Path serien = Files.createDirectories(library.resolve("serien"));
        Path lostNfo = givenFile(serien.resolve("Lost/tvshow.nfo"),
                "<tvshow><title>Lost</title><tag>Krimi</tag></tvshow>");
        givenFile(serien.resolve("Lost/Lost - S01E01.mkv"), "");
        scan(createSource(filme, "MOVIE"));
        scan(createSource(serien, "TV_SHOW"));
        long heat = id("SELECT id FROM movie WHERE title = 'Heat'");
        long ronin = id("SELECT id FROM movie WHERE title = 'Ronin'");
        long lost = id("SELECT id FROM tv_show");

        assertThat(mockMvc.get().uri(TagController.BASE_PATH)).hasStatusOk().bodyJson().isStrictlyEqualTo(
                "[{\"name\": \"Krimi\", \"movies\": 1, \"shows\": 1}, {\"name\": \"Spencer\", \"movies\": 1, \"shows\": 0}]");
        assertThat(mockMvc.get().uri(TagController.BASE_PATH + "/items?tag=Krimi")).hasStatusOk().bodyJson()
                .isStrictlyEqualTo(("{\"tag\": \"Krimi\", \"movies\": [{\"id\": %d, \"title\": \"Heat\"}], "
                        + "\"shows\": [{\"id\": %d, \"title\": \"Lost\"}]}").formatted(heat, lost));

        JsonNode renamed = post("/rename", "{\"tag\": \"Spencer\", \"newName\": \"Bud Spencer\"}");
        assertThat(renamed.get("changed").asInt()).isEqualTo(1);
        assertThat(Files.readString(heatNfo)).contains("<tag>Bud Spencer</tag>", "Al Pacino")
                .doesNotContain("<tag>Spencer</tag>");

        JsonNode assigned = post("/assign", "{\"tag\": \"Neu\", \"movieIds\": [%d, %d], \"showIds\": [%d]}"
                .formatted(heat, ronin, lost));
        assertThat(assigned.get("changed").asInt()).isEqualTo(3);
        assertThat(Files.readString(roninNfo)).contains("<tag>Neu</tag>");
        assertThat(Files.readString(lostNfo)).contains("<tag>Krimi</tag>", "<tag>Neu</tag>");

        post("/unassign", "{\"tag\": \"Neu\", \"movieIds\": [%d], \"showIds\": []}".formatted(ronin));
        assertThat(Files.readString(roninNfo)).doesNotContain("<tag>");

        JsonNode deleted = post("/delete", "{\"tag\": \"Krimi\"}");
        assertThat(deleted.get("changed").asInt()).isEqualTo(2);
        assertThat(Files.readString(lostNfo)).doesNotContain("Krimi");
        assertThat(mockMvc.get().uri(TagController.BASE_PATH)).hasStatusOk().bodyJson().isStrictlyEqualTo(
                "[{\"name\": \"Bud Spencer\", \"movies\": 1, \"shows\": 0}, {\"name\": \"Neu\", \"movies\": 1, \"shows\": 1}]");
    }

    @Test
    void keepsChangesMadeOutsideKmmWhenAssigningATag() throws IOException {
        Path filme = Files.createDirectories(library.resolve("filme"));
        Path heatNfo = givenFile(filme.resolve("Heat/Heat.nfo"), "<movie><title>Heat</title></movie>");
        givenFile(filme.resolve("Heat/Heat.mkv"), "");
        Path serien = Files.createDirectories(library.resolve("serien"));
        Path lostNfo = givenFile(serien.resolve("Lost/tvshow.nfo"), "<tvshow><title>Lost</title></tvshow>");
        givenFile(serien.resolve("Lost/Lost - S01E01.mkv"), "");
        scan(createSource(filme, "MOVIE"));
        scan(createSource(serien, "TV_SHOW"));
        changedOutside(heatNfo, "<movie><title>Heat</title><userrating>10</userrating><playcount>4</playcount>"
                + "<tag>Ember</tag></movie>");
        changedOutside(lostNfo, "<tvshow><title>Lost</title><plot>Flug 815</plot><tag>Ember</tag></tvshow>");

        post("/assign", "{\"tag\": \"Neu\", \"movieIds\": [%d], \"showIds\": [%d]}".formatted(
                id("SELECT id FROM movie WHERE title = 'Heat'"), id("SELECT id FROM tv_show")));

        assertThat(Files.readString(heatNfo)).contains("<userrating>10</userrating>", "<playcount>4</playcount>",
                "<tag>Ember</tag>", "<tag>Neu</tag>");
        assertThat(Files.readString(lostNfo)).contains("<plot>Flug 815</plot>", "<tag>Ember</tag>", "<tag>Neu</tag>");
    }

    @Test
    void rejectsEmptyNames() {
        assertThat(mockMvc.post().uri(TagController.BASE_PATH + "/assign").with(csrf()).contentType(JSON)
                .content("{\"tag\": \"  \", \"movieIds\": [], \"showIds\": []}").exchange())
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    private JsonNode post(String path, String body) {
        return runJob(mockMvc.post().uri(TagController.BASE_PATH + path).with(csrf()).contentType(JSON).content(body)
                .exchange());
    }

    private long id(String sql) {
        return jdbcClient.sql(sql).query(Long.class).single();
    }

    private static void changedOutside(Path nfo, String content) throws IOException {
        Files.writeString(nfo, content);
        Files.setLastModifiedTime(nfo, FileTime.from(Instant.now().plusSeconds(60)));
    }

    private Path givenFile(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        return Files.writeString(file, content);
    }

    private long createSource(Path path, String mediaType) {
        MvcTestResult result = mockMvc.post().uri(MediaSourceController.BASE_PATH).with(csrf()).contentType(JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", path.getFileName().toString(), "path",
                        path.toString(), "mediaType", mediaType)))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return read(result).get("id").asLong();
    }

    private void scan(long sourceId) {
        runJob(mockMvc.post().uri(LibraryScanController.SCAN_PATH, sourceId).with(csrf()).exchange());
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

    private JsonNode read(MvcTestResult result) {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }
}
