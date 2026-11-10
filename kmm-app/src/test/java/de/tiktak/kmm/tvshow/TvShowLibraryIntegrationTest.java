package de.tiktak.kmm.tvshow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
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
 * TV show source via the REST API: create, scan, query shows and episodes, edit (NFOs are written)
 * and scan again.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser
class TvShowLibraryIntegrationTest {

    private static final String JSON = "application/json";
    private static final Duration JOB_TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcClient jdbcClient;

    @TempDir
    Path library;

    @AfterEach
    void deleteLibrary() {
        jdbcClient.sql("DELETE FROM media_source").update();
    }

    @Test
    void scansShowsWithNfosAndKeepsThemInSync() throws IOException {
        createFile("ALF (1986)/Staffel 1/ALF - S01E02 - Der Fremde.avi");
        createFile("ALF (1986)/Staffel 1/ALF - S01E16E17 - Gestatten.avi");
        createFile("ALF (1986)/Staffel 1/So1E99.avi");
        Files.writeString(library.resolve("ALF (1986)/tvshow.nfo"), """
                <tvshow><title>ALF</title><id>78020</id><imdb>tt0090390</imdb><premiered>1986-09-22</premiered>
                <genre>Komödie</genre><status>Ended</status><namedseason number="1">Ankunft</namedseason>
                <actor><name>Paul Fusco</name><role>ALF</role></actor></tvshow>""");
        Path multiNfo = library.resolve("ALF (1986)/Staffel 1/ALF - S01E16E17 - Gestatten.nfo");
        Files.writeString(multiNfo, """
                <episodedetails><title>Gestatten – Teil 1</title><season>1</season><episode>16</episode>
                <director>Tom Patchett</director><playcount>1</playcount>
                <fileinfo><streamdetails><audio><codec>ac3</codec></audio></streamdetails></fileinfo></episodedetails>
                <episodedetails><title>Gestatten – Teil 2</title><season>1</season><episode>17</episode></episodedetails>
                """);
        createFile("Chernobyl (2019)/S01E01.Stunde Null.mp4");
        long sourceId = createSource();

        JsonNode result = scan(sourceId);
        assertThat(result.get("showsAdded").asInt()).isEqualTo(2);
        assertThat(result.get("added").asInt()).isEqualTo(4);
        assertThat(result.get("nfoErrors").asInt()).isZero();
        assertThat(result.get("unrecognizedFiles").toString()).contains("So1E99.avi");
        JsonNode details = result.get("details");
        assertThat(details.get("addedShows")).extracting(show -> show.get("label").asString())
                .containsExactlyInAnyOrder("ALF", "Chernobyl");
        assertThat(details.get("added")).extracting(episode -> episode.get("label").asString())
                .contains("ALF – S01E02 – Der Fremde", "Chernobyl – S01E01 – Stunde Null");
        assertThat(details.get("added")).allSatisfy(episode -> {
            assertThat(episode.get("type").asString()).isEqualTo("TV_EPISODE");
            assertThat(episode.get("id").isNumber()).isTrue();
        });

        assertThat(mockMvc.get().uri(TvShowController.SHOWS_PATH)).bodyJson().isLenientlyEqualTo("""
                {"totalElements": 2, "content": [{"title": "ALF", "year": 1986, "episodeCount": 3},
                                                 {"title": "Chernobyl", "year": 2019, "episodeCount": 1}]}
                """);
        long alfId = idOf(mockMvc.get().uri(TvShowController.SHOWS_PATH).param("search", "alf").exchange());
        JsonNode alf = read(mockMvc.get().uri(TvShowController.SHOW_PATH, alfId).exchange());
        assertThat(alf.toString()).contains("\"tvdbId\":\"78020\"", "\"imdbId\":\"tt0090390\"", "\"status\":\"Ended\"",
                "\"name\":\"Paul Fusco\"");
        assertThat(alf.get("seasons").toString()).contains("{\"number\":1,\"title\":\"Ankunft\",\"episodeCount\":3}");
        assertThat(alf.get("episodes")).extracting(node -> node.get("title").asString())
                .containsExactly("Der Fremde", "Gestatten – Teil 1", "Gestatten – Teil 2");

        long partTwo = alf.get("episodes").get(2).get("id").asLong();
        assertThat(mockMvc.put().uri(TvShowController.EPISODE_METADATA_PATH, partTwo).with(csrf()).contentType(JSON)
                .content("{\"title\": \"Gestatten – Teil 2 (neu)\", \"aired\": \"1987-02-16\", \"tvdbId\": \"110002\"}"))
                .hasStatusOk().bodyJson().extractingPath("$.nfo.exists").isEqualTo(true);
        String written = Files.readString(multiNfo);
        assertThat(written).contains("<title>Gestatten – Teil 1</title>", "<title>Gestatten – Teil 2 (neu)</title>",
                "<director>Tom Patchett</director>", "<codec>ac3</codec>", ">110002</uniqueid>");

        assertThat(mockMvc.put().uri(TvShowController.SHOW_METADATA_PATH, alfId).with(csrf()).contentType(JSON)
                .content("""
                        {"title": "ALF – Der Außerirdische", "premiered": "1986-09-22", "genres": ["Komödie"],
                         "studios": ["NBC"], "tags": [], "tvdbId": "78020"}
                        """))
                .hasStatusOk();
        assertThat(Files.readString(library.resolve("ALF (1986)/tvshow.nfo")))
                .contains("<title>ALF – Der Außerirdische</title>", "<studio>NBC</studio>",
                        "<namedseason number=\"1\">Ankunft</namedseason>", "<name>Paul Fusco</name>")
                .doesNotContain("<id>78020</id>");

        JsonNode unchanged = scan(sourceId);
        assertThat(unchanged.get("updated").asInt()).isZero();
        assertThat(unchanged.get("unchanged").asInt()).isEqualTo(4);

        Files.writeString(multiNfo, written.replace("Teil 1<", "Teil 1 (geändert)<"));
        Files.setLastModifiedTime(multiNfo, FileTime.from(Instant.now().plusSeconds(5)));
        Files.delete(library.resolve("ALF (1986)/Staffel 1/ALF - S01E02 - Der Fremde.avi"));
        deleteRecursively(library.resolve("Chernobyl (2019)"));
        JsonNode changed = scan(sourceId);
        assertThat(changed.get("updated").asInt()).isEqualTo(2);
        assertThat(changed.get("removed").asInt()).isEqualTo(2);
        assertThat(changed.get("showsRemoved").asInt()).isEqualTo(1);
        long partOne = read(mockMvc.get().uri(TvShowController.SHOW_PATH, alfId).exchange())
                .get("episodes").get(0).get("id").asLong();
        assertThat(mockMvc.get().uri(TvShowController.EPISODE_PATH, partOne)).bodyJson().isLenientlyEqualTo("""
                {"showTitle": "ALF – Der Außerirdische", "season": 1, "episode": 16,
                 "metadata": {"title": "Gestatten – Teil 1 (geändert)"},
                 "extras": {"directors": ["Tom Patchett"], "playCount": 1,
                            "streams": {"audios": [{"codec": "ac3"}]}}}
                """);
    }

    @Test
    void readsTheNfoOfAnEmberEpisodeBeforeWritingIt() throws IOException {
        createFile("ALF (1986)/Staffel 1/ALF - S01E02 - Der Fremde.avi");
        Path nfo = library.resolve("ALF (1986)/Staffel 1/ALF - S01E02 - Der Fremde.nfo");
        Files.writeString(nfo, """
                <episodedetails><title>Der Fremde</title><season>1</season><episode>2</episode>
                <director>Tom Patchett</director><userrating>10</userrating>
                <actor><name>Paul Fusco</name><role>ALF</role><tmdbid>1234</tmdbid></actor></episodedetails>""");
        scan(createSource());
        long alfId = idOf(mockMvc.get().uri(TvShowController.SHOWS_PATH).param("search", "alf").exchange());
        long episodeId = read(mockMvc.get().uri(TvShowController.SHOW_PATH, alfId).exchange())
                .get("episodes").get(0).get("id").asLong();
        jdbcClient.sql("DELETE FROM episode_credit WHERE episode_id = :id").param("id", episodeId).update();
        jdbcClient.sql("UPDATE episode SET nfo_read = FALSE, user_rating = NULL WHERE id = :id")
                .param("id", episodeId).update();

        JsonNode opened = read(mockMvc.get().uri(TvShowController.EPISODE_PATH, episodeId).exchange());
        assertThat(opened.get("metadata").get("userRating").asInt()).as("value from the NFO, not Ember's").isEqualTo(10);
        ((tools.jackson.databind.node.ObjectNode) opened.get("metadata")).put("title", "Der Fremde (neu)");
        assertThat(mockMvc.put().uri(TvShowController.EPISODE_METADATA_PATH, episodeId).with(csrf()).contentType(JSON)
                .content(opened.get("metadata").toString())).hasStatusOk();

        assertThat(Files.readString(nfo)).contains("<title>Der Fremde (neu)</title>", "<userrating>10</userrating>",
                "<director>Tom Patchett</director>", "<name>Paul Fusco</name>", "<tmdbid>1234</tmdbid>");
        assertThat(mockMvc.get().uri(TvShowController.EPISODE_PATH, episodeId)).bodyJson().isLenientlyEqualTo("""
                {"extras": {"directors": ["Tom Patchett"], "actors": [{"name": "Paul Fusco"}]}}
                """);
    }

    @Test
    void sortsTheListByEveryColumn() throws IOException {
        createFile("beta (2001)/S01E01.avi");
        createFile("beta (2001)/S01E02.avi");
        createFile("Alpha (2005)/S01E01.avi");
        createFile("Gamma (1999)/S01E01.avi");
        createFile("Gamma (1999)/S01E02.avi");
        createFile("Gamma (1999)/S01E03.avi");
        scan(createSource());

        assertThat(sortedTitles("title,asc,ignorecase")).containsExactly("Alpha", "beta", "Gamma");
        assertThat(sortedTitles("releaseYear,desc")).containsExactly("Alpha", "beta", "Gamma");
        assertThat(sortedTitles("episodeCount,desc")).containsExactly("Gamma", "beta", "Alpha");
        assertThat(sortedTitles("episodeCount,asc")).containsExactly("Alpha", "beta", "Gamma");
        assertThat(sortedTitles("directoryPath,desc,ignorecase")).containsExactly("Gamma", "beta", "Alpha");
        assertThat(sortedTitles("addedAt,asc")).hasSize(3);
    }

    @Test
    void answersUnknownShowsAndEpisodesAndRejectsInvalidMetadata() {
        assertThat(mockMvc.get().uri(TvShowController.SHOW_PATH, 999L)).hasStatus(404);
        assertThat(mockMvc.get().uri(TvShowController.EPISODE_PATH, 999L)).hasStatus(404);
        assertThat(mockMvc.put().uri(TvShowController.EPISODE_METADATA_PATH, 999L).with(csrf()).contentType(JSON)
                .content("{\"title\": \" \"}")).hasStatus(400);
        assertThat(mockMvc.get().uri(TvShowController.SHOWS_PATH).param("sort", "foo")).hasStatus(400);
    }

    @Test
    void scansASingleShowOrSeasonWithoutTouchingTheOthers() throws IOException {
        createFile("ALF (1986)/Staffel 1/ALF - S01E01.avi");
        createFile("ALF (1986)/Staffel 2/ALF - S02E01.avi");
        createFile("Chernobyl (2019)/S01E01.mp4");
        long sourceId = createSource();
        scan(sourceId);
        long alfId = idOf(mockMvc.get().uri(TvShowController.SHOWS_PATH).param("search", "alf").exchange());
        long chernobylId = idOf(mockMvc.get().uri(TvShowController.SHOWS_PATH).param("search", "chernobyl").exchange());
        createFile("ALF (1986)/Staffel 1/ALF - S01E02.avi");
        createFile("ALF (1986)/Staffel 2/ALF - S02E02.avi");
        Files.delete(library.resolve("ALF (1986)/Staffel 2/ALF - S02E01.avi"));
        createFile("Chernobyl (2019)/S01E02.mp4");

        JsonNode season = finished(mockMvc.post().uri(TvShowFolderScanController.SEASON_SCAN_PATH, alfId, 1)
                .with(csrf()).exchange());
        assertThat(season.get("result").get("added").asInt()).isEqualTo(1);
        assertThat(season.get("result").get("removed").asInt()).isZero();
        assertThat(episodeCodes(alfId)).containsExactly("1/1", "1/2", "2/1");

        JsonNode show = finished(mockMvc.post().uri(TvShowFolderScanController.SHOW_SCAN_PATH, alfId).with(csrf())
                .exchange());
        assertThat(show.get("result").get("added").asInt()).isEqualTo(1);
        assertThat(show.get("result").get("removed").asInt()).isEqualTo(1);
        assertThat(show.get("result").get("details").get("removed").get(0).get("label").asString())
                .contains("S02E01");
        assertThat(episodeCodes(alfId)).containsExactly("1/1", "1/2", "2/2");
        assertThat(episodeCodes(chernobylId)).containsExactly("1/1");

        deleteRecursively(library.resolve("ALF (1986)"));
        JsonNode failed = job(mockMvc.post().uri(TvShowFolderScanController.SHOW_SCAN_PATH, alfId).with(csrf())
                .exchange());
        assertThat(failed.get("status").asString()).isEqualTo("FAILED");
        assertThat(failed.get("message").get("key").asString()).isEqualTo("tvShow.folderScan.unreachable");
        assertThat(episodeCodes(alfId)).containsExactly("1/1", "1/2", "2/2");

        JsonNode batch = finished(mockMvc.post().uri(TvShowFolderScanController.SHOWS_SCAN_PATH).with(csrf())
                .contentType(JSON).content("{\"showIds\": [%d, %d]}".formatted(alfId, chernobylId)).exchange());
        assertThat(batch.get("result").get("changed").asInt()).isEqualTo(1);
        assertThat(batch.get("result").get("failed").asInt()).isEqualTo(1);
        assertThat(episodeCodes(chernobylId)).containsExactly("1/1", "1/2");
        assertThat(mockMvc.post().uri(TvShowFolderScanController.SEASON_SCAN_PATH, chernobylId, -1).with(csrf()))
                .hasStatus(400);
        assertThat(mockMvc.post().uri(TvShowFolderScanController.SHOW_SCAN_PATH, 999L).with(csrf())).hasStatus(404);
    }

    @Test
    void rejectsAScanOfAnUnreachableSource() throws IOException {
        Path shows = Files.createDirectories(library.resolve("Serien"));
        long sourceId = createSource(shows);
        deleteRecursively(shows);

        assertThat(mockMvc.post().uri(LibraryScanController.SCAN_PATH, sourceId).with(csrf())).hasStatus(422);
    }

    private long createSource() {
        return createSource(library);
    }

    private long createSource(Path directory) {
        MvcTestResult result = mockMvc.post().uri(MediaSourceController.BASE_PATH).with(csrf()).contentType(JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of("name", "Serien", "path", directory.toString(), "mediaType", "TV_SHOW")))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return read(result).get("id").asLong();
    }

    private JsonNode scan(long sourceId) {
        return finished(mockMvc.post().uri(LibraryScanController.SCAN_PATH, sourceId).with(csrf()).exchange())
                .get("result");
    }

    /** Waits for the started job and expects it to succeed. */
    private JsonNode finished(MvcTestResult started) {
        JsonNode job = job(started);
        assertThat(job.get("status").asString()).as(job.toString()).isEqualTo("SUCCEEDED");
        return job;
    }

    /** Waits for the started job to end. */
    private JsonNode job(MvcTestResult started) {
        assertThat(started).hasStatus(HttpStatus.ACCEPTED);
        String jobId = read(started).get("id").asString();
        return await().atMost(JOB_TIMEOUT).pollInSameThread()
                .until(() -> read(mockMvc.get().uri(JobController.BASE_PATH + "/" + jobId).exchange()),
                        node -> !"RUNNING".equals(node.get("status").asString()));
    }

    /** Season and episode number of all episodes of the TV show, e.g. "1/2". */
    private List<String> episodeCodes(long showId) {
        return read(mockMvc.get().uri(TvShowController.SHOW_PATH, showId).exchange()).get("episodes").valueStream()
                .map(episode -> episode.get("season").asInt() + "/" + episode.get("episode").asInt())
                .toList();
    }

    private List<String> sortedTitles(String sort) {
        MvcTestResult result = mockMvc.post().uri(TvShowController.QUERY_PATH).param("sort", sort).with(csrf())
                .contentType(JSON).content("{\"matchAll\": true, \"conditions\": []}").exchange();
        return read(result).get("content").valueStream().map(show -> show.get("title").asString()).toList();
    }

    private long idOf(MvcTestResult page) {
        return read(page).get("content").get(0).get("id").asLong();
    }

    private JsonNode read(MvcTestResult result) {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private void createFile(String relativePath) throws IOException {
        Path file = library.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.createFile(file);
    }

    private static void deleteRecursively(Path directory) throws IOException {
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
