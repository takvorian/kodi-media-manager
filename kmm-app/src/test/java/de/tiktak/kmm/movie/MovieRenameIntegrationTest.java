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
import de.tiktak.kmm.settings.RenameSettings;
import de.tiktak.kmm.settings.SettingsService;
import de.tiktak.kmm.source.MediaSourceController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Vorschau des Umbenennens Ende zu Ende: Werte aus den NFOs, gemeinsame Ziele als Konflikt, nichts wird
 * geändert.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser
class MovieRenameIntegrationTest {

    private static final String JSON = "application/json";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private SettingsService settingsService;

    @TempDir
    Path library;

    @AfterEach
    void cleanUp() {
        jdbcClient.sql("DELETE FROM media_source").update();
        settingsService.update(settingsService.current().withRename(RenameSettings.DEFAULTS));
    }

    @Test
    void renamesAfterSavingOnlyWhenEnabled() throws IOException {
        Path filme = Files.createDirectories(library.resolve("filme"));
        givenFile(filme.resolve("heat/heat.nfo"), "<movie><title>Heat</title></movie>");
        givenFile(filme.resolve("heat/heat.mkv"), "");
        long sourceId = createSource(filme);
        scan(sourceId);
        long movieId = jdbcClient.sql("SELECT id FROM movie").query(Long.class).single();

        assertThat(save(movieId)).hasStatusOk();
        assertThat(filme.resolve("heat/heat.mkv")).as("Auslöser aus").exists();

        RenameSettings defaults = RenameSettings.DEFAULTS;
        settingsService.update(settingsService.current().withRename(new RenameSettings("{title} ({year})", "{title}",
                defaults.tvShowFolder(), defaults.seasonFolder(), defaults.episodeFile(), true, false, false, false,
                false)));

        assertThat(save(movieId)).hasStatusOk().bodyJson().extractingPath("$.filePath")
                .isEqualTo(filme.resolve("Heat (1995)/Heat.mkv").toString());
        assertThat(filme.resolve("Heat (1995)/Heat.nfo")).content().contains("<year>1995</year>");
    }

    private MvcTestResult save(long movieId) {
        return mockMvc.put().uri(MovieController.METADATA_PATH, movieId).with(csrf()).contentType(JSON)
                .content("{\"title\": \"Heat\", \"year\": 1995, \"genres\": []}").exchange();
    }

    @Test
    void previewsTheNewNamesWithoutChangingAnything() throws IOException {
        Path filme = Files.createDirectories(library.resolve("filme"));
        givenFile(filme.resolve("heat/heat.nfo"), """
                <movie><title>Heat</title><year>1995</year><videosource>bluray</videosource></movie>""");
        givenFile(filme.resolve("heat/heat.mkv"), "");
        givenFile(filme.resolve("Ronin (1998)/Ronin (1998).nfo"), "<movie><title>Ronin</title><year>1998</year></movie>");
        givenFile(filme.resolve("Ronin (1998)/Ronin (1998).mkv"), "");
        givenFile(filme.resolve("a/Alien.nfo"), "<movie><title>Same</title></movie>");
        givenFile(filme.resolve("a/Alien.mkv"), "");
        givenFile(filme.resolve("b/Brazil.nfo"), "<movie><title>Same</title></movie>");
        givenFile(filme.resolve("b/Brazil.mkv"), "");
        long sourceId = createSource(filme);
        scan(sourceId);

        MvcTestResult preview = mockMvc.post().uri(MovieRenameController.SOURCE_PREVIEW_PATH, sourceId).with(csrf())
                .contentType(JSON).content("""
                        {"folder": "{title}[ ({year})]", "file": "{title}[.{source}]"}""").exchange();

        assertThat(preview).hasStatusOk();
        JsonNode plans = read(preview);
        assertThat(plans).hasSize(4);
        assertThat(plans.get(0).get("status").asString()).isEqualTo("CONFLICT");
        assertThat(plans.get(0).get("message").asString()).startsWith("Mehrere Filme bekämen den Ordner");
        JsonNode heat = plans.get(2);
        assertThat(heat.get("title").asString()).isEqualTo("Heat");
        assertThat(heat.get("status").asString()).isEqualTo("RENAME");
        assertThat(heat.get("targetDirectory").asString()).isEqualTo(filme.resolve("Heat (1995)").toString());
        assertThat(heat.get("renames").toString()).contains("\"currentName\":\"heat.mkv\"",
                "\"targetName\":\"Heat.bluray.mkv\"");
        assertThat(plans.get(3).get("status").asString()).as("Ronin.mkv statt Ronin (1998).mkv").isEqualTo("RENAME");
        assertThat(filme.resolve("heat/heat.mkv")).exists();
    }

    @Test
    void renamesAMovieAndTheNextScanFindsItAtItsNewPlace() throws IOException {
        Path filme = Files.createDirectories(library.resolve("filme"));
        Path srt = filme.resolve("alt/heat/heat.de.srt");
        givenFile(filme.resolve("alt/heat/heat.nfo"), """
                <movie><title>Heat</title><year>1995</year><playcount>2</playcount>
                <fileinfo><streamdetails><subtitle><language>deu</language><path>%s</path></subtitle>
                </streamdetails></fileinfo></movie>""".formatted(srt));
        givenFile(filme.resolve("alt/heat/heat.mkv"), "");
        givenFile(srt, "");
        long sourceId = createSource(filme);
        scan(sourceId);
        long movieId = jdbcClient.sql("SELECT id FROM movie").query(Long.class).single();

        MvcTestResult renamed = mockMvc.post().uri(MovieRenameController.MOVIE_RENAME_PATH, movieId).with(csrf())
                .contentType(JSON).content("""
                        {"folder": "{title:first}/{title} ({year})", "file": "{title}"}""").exchange();

        assertThat(renamed).hasStatusOk().bodyJson().extractingPath("$.status").isEqualTo("RENAME");
        Path target = filme.resolve("H/Heat (1995)");
        assertThat(target.resolve("Heat.mkv")).exists();
        assertThat(target.resolve("Heat.de.srt")).exists();
        assertThat(filme.resolve("alt")).doesNotExist();
        assertThat(jdbcClient.sql("SELECT file_path FROM movie").query(String.class).single())
                .isEqualTo(target.resolve("Heat.mkv").toString());
        assertThat(Files.readString(target.resolve("Heat.nfo"))).contains("<path>" + target.resolve("Heat.de.srt"))
                .contains("<playcount>2</playcount>");
        JsonNode rescan = scan(sourceId);
        assertThat(rescan.get("added").asInt()).isZero();
        assertThat(rescan.get("removed").asInt()).isZero();
        assertThat(mockMvc.post().uri(MovieRenameController.MOVIE_PREVIEW_PATH, movieId).with(csrf())
                .contentType(JSON).content("""
                        {"folder": "{title:first}/{title} ({year})", "file": "{title}"}""").exchange())
                .hasStatusOk().bodyJson().extractingPath("$.status").isEqualTo("UNCHANGED");
    }

    @Test
    void renamesTheSelectedMoviesOfASourceAndSkipsConflicts() throws IOException {
        Path filme = Files.createDirectories(library.resolve("filme"));
        givenFile(filme.resolve("heat/heat.nfo"), "<movie><title>Heat</title></movie>");
        givenFile(filme.resolve("heat/heat.mkv"), "");
        givenFile(filme.resolve("ronin/ronin.nfo"), "<movie><title>Ronin</title></movie>");
        givenFile(filme.resolve("ronin/ronin.mkv"), "");
        givenFile(filme.resolve("Ronin2/belegt.txt"), "");
        givenFile(filme.resolve("alien/alien.nfo"), "<movie><title>Alien</title></movie>");
        givenFile(filme.resolve("alien/alien.mkv"), "");
        long sourceId = createSource(filme);
        scan(sourceId);
        long alienId = jdbcClient.sql("SELECT id FROM movie WHERE title = 'Alien'").query(Long.class).single();
        long heatId = jdbcClient.sql("SELECT id FROM movie WHERE title = 'Heat'").query(Long.class).single();

        JsonNode result = runJob(mockMvc.post().uri(MovieRenameController.SOURCE_RENAME_PATH, sourceId).with(csrf())
                .contentType(JSON).content("""
                        {"patterns": {"folder": "{title}2", "file": "{title}"}, "movieIds": [%d, %d]}"""
                        .formatted(alienId, heatId)).exchange());

        assertThat(result.get("renamed").asInt()).isEqualTo(2);
        assertThat(filme.resolve("Alien2/Alien.mkv")).exists();
        assertThat(filme.resolve("Heat2/Heat.mkv")).exists();
        assertThat(filme.resolve("ronin/ronin.mkv")).as("nicht ausgewählt").exists();

        JsonNode all = runJob(mockMvc.post().uri(MovieRenameController.SOURCE_RENAME_PATH, sourceId).with(csrf())
                .contentType(JSON).content("""
                        {"patterns": {"folder": "{title}2", "file": "{title}"}}""").exchange());

        assertThat(all.get("renamed").asInt()).isZero();
        assertThat(all.get("unchanged").asInt()).isEqualTo(2);
        assertThat(all.get("skipped").asInt()).isEqualTo(1);
        assertThat(all.get("problems").get(0).asString()).startsWith("„Ronin“: Der Ordner");
    }

    @Test
    void rejectsInvalidPatternsAndOtherSources() throws IOException {
        long sourceId = createSource(Files.createDirectories(library.resolve("filme")));

        assertThat(mockMvc.post().uri(MovieRenameController.SOURCE_PREVIEW_PATH, sourceId).with(csrf())
                .contentType(JSON).content("""
                        {"folder": "{titel}", "file": "{title}"}""").exchange())
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT).bodyJson().extractingPath("$.detail")
                .isEqualTo("Ordnermuster: Unbekannter Platzhalter „{titel}“ (Stelle 1).");
        assertThat(mockMvc.post().uri(MovieRenameController.SOURCE_PREVIEW_PATH, sourceId).with(csrf())
                .contentType(JSON).content("""
                        {"folder": "{title}", "file": "{title:first}/{title}"}""").exchange())
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(mockMvc.post().uri(MovieRenameController.SOURCE_PREVIEW_PATH, sourceId).with(csrf())
                .contentType(JSON).content("""
                        {"folder": "", "file": "{title}"}""").exchange())
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    private void givenFile(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private long createSource(Path path) {
        MvcTestResult result = mockMvc.post().uri(MediaSourceController.BASE_PATH).with(csrf()).contentType(JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Filme", "path", path.toString(),
                        "mediaType", "MOVIE")))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return read(result).get("id").asLong();
    }

    private JsonNode scan(long sourceId) {
        return runJob(mockMvc.post().uri(LibraryScanController.SCAN_PATH, sourceId).with(csrf()).exchange());
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
