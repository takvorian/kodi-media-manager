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
import de.tiktak.kmm.plugin.api.CollectionReference;
import de.tiktak.kmm.library.LibraryScanController;
import de.tiktak.kmm.source.MediaSourceController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Filmset-Manager Ende zu Ende: anlegen, Filme zuordnen, umbenennen, sortieren, entfernen und löschen; die NFOs der
 * Filme tragen danach das richtige Set.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser
class MovieSetIntegrationTest {

    private static final String JSON = "application/json";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private MovieCollectionAssigner collectionAssigner;

    @TempDir
    Path library;

    @AfterEach
    void cleanUp() {
        jdbcClient.sql("DELETE FROM media_source").update();
        jdbcClient.sql("DELETE FROM movie_set").update();
    }

    @Test
    void managesASetAndWritesItIntoTheMovieNfos() throws IOException {
        Path filme = Files.createDirectories(library.resolve("filme"));
        Path matrixNfo = givenMovie(filme, "Matrix", 1999, "<set><name>Alt</name></set>");
        Path reloadedNfo = givenMovie(filme, "Matrix Reloaded", 2003, "");
        givenMovie(filme, "Animatrix", 2003, "<sorttitle>Matrix Animatrix</sorttitle>");
        scan(filme);
        long matrix = movieId("Matrix");
        long reloaded = movieId("Matrix Reloaded");
        long animatrix = movieId("Animatrix");
        long alt = jdbcClient.sql("SELECT id FROM movie_set WHERE name = 'Alt'").query(Long.class).single();

        MvcTestResult created = mockMvc.post().uri(MovieSetController.BASE_PATH).with(csrf()).contentType(JSON)
                .content("{\"name\": \"Matrix Filmreihe\", \"overview\": \"Neo\", \"tmdbCollectionId\": \"2344\","
                        + " \"sortMethod\": \"YEAR\"}").exchange();
        assertThat(created).hasStatus(HttpStatus.CREATED);
        long setId = read(created).get("id").asLong();
        assertThat(mockMvc.post().uri(MovieSetController.BASE_PATH).with(csrf()).contentType(JSON)
                .content("{\"name\": \"matrix filmreihe\", \"sortMethod\": \"YEAR\"}").exchange())
                .hasStatus(HttpStatus.CONFLICT);

        JsonNode added = runJob(mockMvc.post().uri(MovieSetController.BASE_PATH + "/{id}/movies", setId).with(csrf())
                .contentType(JSON).content("{\"movieIds\": [%d, %d, %d]}".formatted(reloaded, matrix, animatrix))
                .exchange());
        assertThat(added.get("changed").asInt()).isEqualTo(3);
        assertThat(Files.readString(matrixNfo)).contains("<name>Matrix Filmreihe</name>", "<overview>Neo</overview>")
                .doesNotContain("Alt");
        assertThat(titles(setId)).containsExactly("Matrix", "Animatrix", "Matrix Reloaded");

        runJob(mockMvc.put().uri(MovieSetController.BASE_PATH + "/{id}", setId).with(csrf()).contentType(JSON)
                .content("{\"name\": \"Matrix Trilogie\", \"overview\": \"Neo\", \"sortMethod\": \"TITLE\"}")
                .exchange());
        assertThat(Files.readString(reloadedNfo)).contains("<name>Matrix Trilogie</name>");
        assertThat(titles(setId)).as("nach Sortiertitel").containsExactly("Matrix", "Animatrix", "Matrix Reloaded");
        JsonNode sets = read(mockMvc.get().uri(MovieSetController.BASE_PATH).exchange());
        assertThat(sets.valueStream().filter(set -> set.get("id").asLong() == alt || set.get("id").asLong() == setId)
                .map(set -> set.get("name").asString() + ":" + set.get("movies").asInt() + ":"
                        + set.get("sortMethod").asString() + ":" + set.get("tmdbCollectionId").isNull()))
                .containsExactly("Alt:0:YEAR:true", "Matrix Trilogie:3:TITLE:true");

        runJob(mockMvc.post().uri(MovieSetController.BASE_PATH + "/{id}/movies/remove", setId).with(csrf())
                .contentType(JSON).content("{\"movieIds\": [%d]}".formatted(matrix)).exchange());
        assertThat(Files.readString(matrixNfo)).doesNotContain("<set>");

        runJob(mockMvc.delete().uri(MovieSetController.BASE_PATH + "/{id}", setId).with(csrf()).exchange());
        assertThat(Files.readString(reloadedNfo)).doesNotContain("<set>");
        assertThat(mockMvc.get().uri(MovieSetController.BASE_PATH + "/{id}", setId))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void assignsScrapedMoviesToTheSetOfTheirCollection() throws IOException {
        Path filme = Files.createDirectories(library.resolve("filme"));
        Path matrixNfo = givenMovie(filme, "Matrix", 1999, "<set><name>Alt</name></set>");
        givenMovie(filme, "Matrix Reloaded", 2003, "");
        givenMovie(filme, "Heat", 1995, "");
        scan(filme);
        CollectionReference matrix = new CollectionReference("2344", "Matrix Filmreihe");

        assertThat(collectionAssigner.assign(movieId("Matrix"), matrix)).isTrue();
        assertThat(collectionAssigner.assign(movieId("Matrix Reloaded"), new CollectionReference("2344", "Matrix")))
                .isTrue();
        assertThat(collectionAssigner.assign(movieId("Matrix"), matrix)).isFalse();
        jdbcClient.sql("INSERT INTO movie_set (name, sort_method) VALUES ('Heat-Reihe', 'YEAR')").update();
        assertThat(collectionAssigner.assign(movieId("Heat"), new CollectionReference("99", "HEAT-REIHE"))).isTrue();

        assertThat(Files.readString(matrixNfo)).contains("<name>Matrix Filmreihe</name>").doesNotContain("Alt");
        assertThat(jdbcClient.sql("SELECT name || ':' || tmdb_collection_id FROM movie_set m WHERE EXISTS "
                        + "(SELECT 1 FROM movie WHERE movie_set_id = m.id) ORDER BY name").query(String.class).list())
                .containsExactly("Heat-Reihe:99", "Matrix Filmreihe:2344");
    }

    @Test
    void readsANfoChangedOutsideKmmBeforeRewritingIt() throws IOException {
        Path filme = Files.createDirectories(library.resolve("filme"));
        Path nfo = givenMovie(filme, "Alles steht Kopf 2", 2024, "");
        scan(filme);
        Files.writeString(nfo, "<movie><title>Alles steht Kopf 2</title><year>2024</year><userrating>10</userrating>"
                + "<playcount>4</playcount><tag>Pixar</tag></movie>");
        Files.setLastModifiedTime(nfo, FileTime.from(Instant.now().plusSeconds(60)));
        MvcTestResult created = mockMvc.post().uri(MovieSetController.BASE_PATH).with(csrf()).contentType(JSON)
                .content("{\"name\": \"Alles steht Kopf Filmreihe\", \"tmdbCollectionId\": \"1022790\","
                        + " \"sortMethod\": \"YEAR\"}").exchange();
        long setId = read(created).get("id").asLong();

        JsonNode added = runJob(mockMvc.post().uri(MovieSetController.BASE_PATH + "/{id}/movies", setId).with(csrf())
                .contentType(JSON).content("{\"movieIds\": [%d]}".formatted(movieId("Alles steht Kopf 2"))).exchange());

        assertThat(Files.readString(nfo)).contains("<userrating>10</userrating>", "<playcount>4</playcount>",
                "<tag>Pixar</tag>", "<name>Alles steht Kopf Filmreihe</name>", "<tmdb>1022790</tmdb>");
        assertThat(added.get("changed").asInt()).isEqualTo(1);
    }

    @Test
    void rejectsInvalidSets() {
        assertThat(mockMvc.post().uri(MovieSetController.BASE_PATH).with(csrf()).contentType(JSON)
                .content("{\"name\": \" \", \"sortMethod\": \"YEAR\"}").exchange()).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(mockMvc.post().uri(MovieSetController.BASE_PATH).with(csrf()).contentType(JSON)
                .content("{\"name\": \"X\", \"tmdbCollectionId\": \"abc\", \"sortMethod\": \"YEAR\"}").exchange())
                .hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(mockMvc.delete().uri(MovieSetController.BASE_PATH + "/999").with(csrf()))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    private java.util.List<String> titles(long setId) {
        JsonNode movies = read(mockMvc.get().uri(MovieSetController.BASE_PATH + "/{id}", setId).exchange())
                .get("movies");
        return movies.valueStream().map(movie -> movie.get("title").asString()).toList();
    }

    private Path givenMovie(Path filme, String title, int year, String extra) throws IOException {
        Path folder = Files.createDirectories(filme.resolve(title));
        Files.writeString(folder.resolve(title + ".mkv"), "");
        return Files.writeString(folder.resolve(title + ".nfo"),
                "<movie><title>%s</title><year>%d</year>%s</movie>".formatted(title, year, extra));
    }

    private long movieId(String title) {
        return jdbcClient.sql("SELECT id FROM movie WHERE title = ?").param(title).query(Long.class).single();
    }

    private void scan(Path path) {
        MvcTestResult source = mockMvc.post().uri(MediaSourceController.BASE_PATH).with(csrf()).contentType(JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Filme", "path", path.toString(),
                        "mediaType", "MOVIE")))
                .exchange();
        assertThat(source).hasStatus(HttpStatus.CREATED);
        runJob(mockMvc.post().uri(LibraryScanController.SCAN_PATH, read(source).get("id").asLong()).with(csrf())
                .exchange());
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
