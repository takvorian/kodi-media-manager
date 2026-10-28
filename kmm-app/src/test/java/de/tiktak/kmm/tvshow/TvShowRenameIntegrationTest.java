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
 * Umbenennen von Serien Ende zu Ende: Vorschau mit Werten aus den NFOs, ohne etwas zu ändern.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser
class TvShowRenameIntegrationTest {

    private static final String JSON = "application/json";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String PATTERNS = """
            {"showFolder": "{show} ({year})", "seasonFolder": "Staffel {season}",
             "episodeFile": "{show} - S{season:2}E{episode:2}[ - {title}]"}""";

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
    void renamesAfterSavingAndAfterScanningNewEpisodesWhenEnabled() throws IOException {
        Path serien = Files.createDirectories(library.resolve("serien"));
        givenFile(serien.resolve("lost/tvshow.nfo"), "<tvshow><title>Lost</title><premiered>2004-09-22</premiered></tvshow>");
        givenFile(serien.resolve("lost/lost.s01e01.mkv"), "");
        long sourceId = createSource(serien);
        scan(sourceId);
        long showId = jdbcClient.sql("SELECT id FROM tv_show").query(Long.class).single();
        RenameSettings defaults = RenameSettings.DEFAULTS;
        settingsService.update(settingsService.current().withRename(new RenameSettings(defaults.movieFolder(),
                defaults.movieFile(), "{show} ({year})", "Staffel {season}", "{show} - S{season:2}E{episode:2}",
                false, false, true, false, true)));

        assertThat(mockMvc.put().uri(TvShowController.SHOW_METADATA_PATH, showId).with(csrf()).contentType(JSON)
                .content("""
                        {"title": "Lost", "premiered": "2004-09-22", "genres": [], "studios": [], "tags": []}""")
                .exchange())
                .hasStatusOk().bodyJson().extractingPath("$.directoryPath")
                .isEqualTo(serien.resolve("Lost (2004)").toString());
        assertThat(serien.resolve("Lost (2004)/Staffel 1/Lost - S01E01.mkv")).exists();

        givenFile(serien.resolve("Lost (2004)/lost.s01e02.mkv"), "");
        scan(sourceId);

        assertThat(serien.resolve("Lost (2004)/Staffel 1/Lost - S01E02.mkv")).exists();
    }

    @Test
    void previewsTheNewNamesWithoutChangingAnything() throws IOException {
        Path serien = Files.createDirectories(library.resolve("serien"));
        givenFile(serien.resolve("lost/tvshow.nfo"), "<tvshow><title>Lost</title><premiered>2004-09-22</premiered></tvshow>");
        givenFile(serien.resolve("lost/lost.s01e01e02.nfo"), """
                <episodedetails><title>Pilot (1)</title><season>1</season><episode>1</episode></episodedetails>
                <episodedetails><title>Pilot (2)</title><season>1</season><episode>2</episode></episodedetails>""");
        givenFile(serien.resolve("lost/lost.s01e01e02.mkv"), "");
        long sourceId = createSource(serien);
        scan(sourceId);
        long showId = jdbcClient.sql("SELECT id FROM tv_show").query(Long.class).single();

        MvcTestResult preview = mockMvc.post().uri(TvShowRenameController.SOURCE_PREVIEW_PATH, sourceId).with(csrf())
                .contentType(JSON).content(PATTERNS).exchange();

        assertThat(preview).hasStatusOk();
        JsonNode plan = read(preview).get(0);
        assertThat(plan.get("status").asString()).isEqualTo("RENAME");
        assertThat(plan.get("targetDirectory").asString()).isEqualTo(serien.resolve("Lost (2004)").toString());
        JsonNode file = plan.get("files").get(0);
        assertThat(file.get("episodes").asString()).isEqualTo("S01E01E02");
        assertThat(file.get("targetFolder").asString()).isEqualTo("Staffel 1");
        assertThat(file.get("renames").toString()).contains(
                "\"targetName\":\"Lost - S01E01E02 - Pilot (1) - Pilot (2).mkv\"",
                "\"targetName\":\"Lost - S01E01E02 - Pilot (1) - Pilot (2).nfo\"");
        assertThat(mockMvc.post().uri(TvShowRenameController.SHOW_PREVIEW_PATH, showId).with(csrf())
                .contentType(JSON).content(PATTERNS).exchange())
                .hasStatusOk().bodyJson().extractingPath("$.showId").isEqualTo((int) showId);
        assertThat(serien.resolve("lost/lost.s01e01e02.mkv")).exists();
    }

    @Test
    void renamesAShowAndTheNextScanFindsItsEpisodesAtTheirNewPlace() throws IOException {
        Path serien = Files.createDirectories(library.resolve("serien"));
        Path srt = serien.resolve("lost/lost.s01e01e02.de.srt");
        givenFile(serien.resolve("lost/tvshow.nfo"), "<tvshow><title>Lost</title><premiered>2004-09-22</premiered></tvshow>");
        givenFile(serien.resolve("lost/lost.s01e01e02.nfo"), """
                <episodedetails><title>Pilot (1)</title><season>1</season><episode>1</episode><playcount>1</playcount>
                <fileinfo><streamdetails><subtitle><language>deu</language><path>%s</path></subtitle></streamdetails>
                </fileinfo></episodedetails>
                <episodedetails><title>Pilot (2)</title><season>1</season><episode>2</episode></episodedetails>"""
                .formatted(srt));
        givenFile(serien.resolve("lost/lost.s01e01e02.mkv"), "");
        givenFile(srt, "");
        long sourceId = createSource(serien);
        scan(sourceId);
        long showId = jdbcClient.sql("SELECT id FROM tv_show").query(Long.class).single();

        MvcTestResult renamed = mockMvc.post().uri(TvShowRenameController.SHOW_RENAME_PATH, showId).with(csrf())
                .contentType(JSON).content(PATTERNS).exchange();

        assertThat(renamed).hasStatusOk().bodyJson().extractingPath("$.status").isEqualTo("RENAME");
        Path season = serien.resolve("Lost (2004)/Staffel 1");
        String name = "Lost - S01E01E02 - Pilot (1) - Pilot (2)";
        assertThat(season.resolve(name + ".mkv")).exists();
        assertThat(season.resolve(name + ".de.srt")).exists();
        assertThat(serien.resolve("Lost (2004)/tvshow.nfo")).exists();
        assertThat(serien.resolve("lost")).doesNotExist();
        assertThat(jdbcClient.sql("SELECT directory_path FROM tv_show").query(String.class).single())
                .isEqualTo(serien.resolve("Lost (2004)").toString());
        assertThat(jdbcClient.sql("SELECT DISTINCT file_path FROM episode").query(String.class).list())
                .containsExactly(season.resolve(name + ".mkv").toString());
        assertThat(Files.readString(season.resolve(name + ".nfo")))
                .contains("<path>" + season.resolve(name + ".de.srt"), "<playcount>1</playcount>", "Pilot (2)");
        JsonNode rescan = scan(sourceId);
        assertThat(rescan.toString()).contains("\"removed\":0");
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM episode").query(Long.class).single()).isEqualTo(2);
        assertThat(mockMvc.post().uri(TvShowRenameController.SHOW_PREVIEW_PATH, showId).with(csrf())
                .contentType(JSON).content(PATTERNS).exchange())
                .hasStatusOk().bodyJson().extractingPath("$.status").isEqualTo("UNCHANGED");
    }

    @Test
    void renamesTheSelectedShowsOfASourceAndReportsConflicts() throws IOException {
        Path serien = Files.createDirectories(library.resolve("serien"));
        givenFile(serien.resolve("alf/tvshow.nfo"), "<tvshow><title>ALF</title><premiered>1986-09-22</premiered></tvshow>");
        givenFile(serien.resolve("alf/alf.s01e01.mkv"), "");
        givenFile(serien.resolve("lost/tvshow.nfo"), "<tvshow><title>Lost</title><premiered>2004-09-22</premiered></tvshow>");
        givenFile(serien.resolve("lost/lost.s01e01.mkv"), "");
        givenFile(serien.resolve("Lost (2004)/belegt.txt"), "");
        long sourceId = createSource(serien);
        scan(sourceId);

        JsonNode result = runJob(mockMvc.post().uri(TvShowRenameController.SOURCE_RENAME_PATH, sourceId).with(csrf())
                .contentType(JSON).content("{\"patterns\": %s}".formatted(PATTERNS)).exchange());

        assertThat(result.get("renamed").asInt()).isEqualTo(1);
        assertThat(result.get("skipped").asInt()).isEqualTo(1);
        assertThat(result.get("problems").get(0).asString()).startsWith("„Lost“: Der Ordner");
        try (var files = Files.list(serien.resolve("ALF (1986)/Staffel 1"))) {
            assertThat(files.map(file -> file.getFileName().toString())).singleElement().asString()
                    .startsWith("ALF - S01E01").endsWith(".mkv");
        }
        assertThat(serien.resolve("lost/lost.s01e01.mkv")).exists();
    }

    @Test
    void rejectsInvalidPatternsAndMovieSources() throws IOException {
        long sourceId = createSource(Files.createDirectories(library.resolve("serien")));

        assertThat(mockMvc.post().uri(TvShowRenameController.SOURCE_PREVIEW_PATH, sourceId).with(csrf())
                .contentType(JSON).content("""
                        {"showFolder": "{show}", "seasonFolder": "{show}/{season}", "episodeFile": "{title}"}""")
                .exchange())
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT).bodyJson().extractingPath("$.detail")
                .isEqualTo("Muster für Staffelordner: Ordner (/) sind hier nicht erlaubt.");
        assertThat(mockMvc.post().uri(TvShowRenameController.SOURCE_PREVIEW_PATH, sourceId).with(csrf())
                .contentType(JSON).content("""
                        {"showFolder": "{title}", "seasonFolder": "x", "episodeFile": "x"}""").exchange())
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        long movies = createSource(Files.createDirectories(library.resolve("filme")), "MOVIE");
        assertThat(mockMvc.post().uri(TvShowRenameController.SOURCE_PREVIEW_PATH, movies).with(csrf())
                .contentType(JSON).content(PATTERNS).exchange())
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    private void givenFile(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private long createSource(Path path) {
        return createSource(path, "TV_SHOW");
    }

    private long createSource(Path path, String mediaType) {
        MvcTestResult result = mockMvc.post().uri(MediaSourceController.BASE_PATH).with(csrf()).contentType(JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", path.getFileName().toString(), "path",
                        path.toString(), "mediaType", mediaType)))
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
