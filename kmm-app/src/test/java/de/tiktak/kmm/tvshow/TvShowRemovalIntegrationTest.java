package de.tiktak.kmm.tvshow;

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
 * Removing episodes, seasons and TV shows end to end via REST and job, from the library only and with the files.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser
class TvShowRemovalIntegrationTest {

    private static final String JSON = "application/json";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String LIBRARY_ONLY = "{\"deleteFiles\": false}";
    private static final String WITH_FILES = "{\"deleteFiles\": true}";

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcClient jdbcClient;

    @TempDir
    Path library;

    private Path alf;
    private long sourceId;

    @BeforeEach
    void createLibrary() throws IOException {
        alf = library.resolve("ALF (1986)");
        Files.createDirectories(alf);
        Files.writeString(alf.resolve("tvshow.nfo"), """
                <tvshow><title>ALF</title><namedseason number="1">Ankunft</namedseason>
                <namedseason number="2">Zweite</namedseason></tvshow>""");
        file("ALF (1986)/poster.jpg");
        file("ALF (1986)/season01-poster.jpg");
        file("ALF (1986)/season02-poster.jpg");
        file("ALF (1986)/Staffel 1/ALF - S01E01 - Anfang.avi");
        file("ALF (1986)/Staffel 1/ALF - S01E01 - Anfang-thumb.jpg");
        file("ALF (1986)/Staffel 1/ALF - S01E02E03 - Doppelt.avi");
        file("ALF (1986)/Staffel 1/ALF - S01E02E03 - Doppelt.de.srt");
        file("ALF (1986)/Staffel 2/ALF - S02E01 - Weiter.avi");
        file("ALF (1986)/Staffel 2/notizen.txt");
        file("Lost/S01E01.avi");
        sourceId = createSource();
        scan();
    }

    @AfterEach
    void deleteLibrary() {
        jdbcClient.sql("DELETE FROM media_source").update();
    }

    @Test
    void removesAnEpisodeFromTheLibraryOnlyAndTheNextScanFindsItAgain() {
        long episodeId = episodeId(1, 1);

        JsonNode plan = read(mockMvc.get().uri(TvShowRemovalController.EPISODE_REMOVAL_PATH, episodeId).exchange());
        assertThat(plan.get("files")).hasSize(2);
        assertThat(plan.get("canDeleteFiles").asBoolean()).isTrue();

        JsonNode result = runJob(mockMvc.post().uri(TvShowRemovalController.EPISODE_REMOVAL_PATH, episodeId)
                .with(csrf()).contentType(JSON).content(LIBRARY_ONLY).exchange());

        assertThat(result.get("removedEntries").asInt()).isEqualTo(1);
        assertThat(result.get("deletedFiles").asInt()).isZero();
        assertThat(alf.resolve("Staffel 1/ALF - S01E01 - Anfang.avi")).exists();
        assertThat(episodeCount()).isEqualTo(3);

        scan();

        assertThat(episodeCount()).isEqualTo(4);
    }

    @Test
    void deletesAMultiEpisodeFileWithAllItsEpisodes() {
        long episodeId = episodeId(1, 3);

        JsonNode plan = read(mockMvc.get().uri(TvShowRemovalController.EPISODE_REMOVAL_PATH, episodeId).exchange());
        assertThat(plan.get("entries")).hasSize(2);
        assertThat(plan.get("notes").get(0).get("key").asString()).isEqualTo("removal.note.multiEpisode");

        JsonNode result = runJob(mockMvc.post().uri(TvShowRemovalController.EPISODE_REMOVAL_PATH, episodeId)
                .with(csrf()).contentType(JSON).content(WITH_FILES).exchange());

        assertThat(result.get("removedEntries").asInt()).isEqualTo(2);
        assertThat(result.get("deletedFiles").asInt()).isEqualTo(2);
        assertThat(alf.resolve("Staffel 1/ALF - S01E02E03 - Doppelt.avi")).doesNotExist();
        assertThat(alf.resolve("Staffel 1/ALF - S01E02E03 - Doppelt.de.srt")).doesNotExist();
        assertThat(alf.resolve("Staffel 1/ALF - S01E01 - Anfang.avi")).exists();
        assertThat(episodeCount()).isEqualTo(2);
    }

    @Test
    void deletesASeasonWithItsFolderArtworkAndDetails() {
        long showId = showId();

        JsonNode result = runJob(mockMvc.post().uri(TvShowRemovalController.SEASON_REMOVAL_PATH, showId, 1)
                .with(csrf()).contentType(JSON).content(WITH_FILES).exchange());

        assertThat(result.get("removedEntries").asInt()).isEqualTo(3);
        assertThat(alf.resolve("Staffel 1")).doesNotExist();
        assertThat(alf.resolve("season01-poster.jpg")).doesNotExist();
        assertThat(alf.resolve("season02-poster.jpg")).exists();
        assertThat(alf.resolve("Staffel 2/ALF - S02E01 - Weiter.avi")).exists();
        assertThat(jdbcClient.sql("SELECT season_number FROM tv_season").query(Integer.class).list())
                .containsExactly(2);
        assertThat(episodeCount()).isEqualTo(1);
        assertThat(mockMvc.get().uri(TvShowRemovalController.SEASON_REMOVAL_PATH, showId, 1).exchange())
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void deletesAWholeShowButNeverTheSource() {
        long showId = showId();

        JsonNode plan = read(mockMvc.get().uri(TvShowRemovalController.SHOW_REMOVAL_PATH, showId).exchange());
        assertThat(plan.get("notes").get(0).get("key").asString()).isEqualTo("removal.note.unknownFiles");
        assertThat(plan.get("directories").get(0).asString()).isEqualTo(alf.toString());

        runJob(mockMvc.post().uri(TvShowRemovalController.SHOW_REMOVAL_PATH, showId).with(csrf()).contentType(JSON)
                .content(WITH_FILES).exchange());

        assertThat(alf).doesNotExist();
        assertThat(library.resolve("Lost/S01E01.avi")).exists();
        assertThat(library).exists();
        assertThat(jdbcClient.sql("SELECT title FROM tv_show").query(String.class).list()).containsExactly("Lost");
        assertThat(mockMvc.get().uri(TvShowController.SHOW_PATH, showId).exchange()).hasStatus(HttpStatus.NOT_FOUND);
    }

    private long createSource() {
        MvcTestResult result = mockMvc.post().uri(MediaSourceController.BASE_PATH).with(csrf()).contentType(JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of("name", "Serien", "path", library.toString(), "mediaType", "TV_SHOW")))
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

    private long showId() {
        return jdbcClient.sql("SELECT id FROM tv_show WHERE title = 'ALF'").query(Long.class).single();
    }

    private long episodeId(int season, int episode) {
        return jdbcClient.sql("SELECT e.id FROM episode e JOIN tv_show s ON s.id = e.tv_show_id "
                + "WHERE s.title = 'ALF' AND e.season_number = ? AND e.episode_number = ?")
                .params(season, episode).query(Long.class).single();
    }

    private int episodeCount() {
        return jdbcClient.sql("SELECT COUNT(*) FROM episode e JOIN tv_show s ON s.id = e.tv_show_id "
                + "WHERE s.title = 'ALF'").query(Integer.class).single();
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
