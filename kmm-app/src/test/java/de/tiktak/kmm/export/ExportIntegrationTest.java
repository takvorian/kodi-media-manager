package de.tiktak.kmm.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.imageio.ImageIO;

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
 * Export von Film- und Serienliste Ende zu Ende.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser
class ExportIntegrationTest {

    private static final String JSON = "application/json";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String ALL = "{\"matchAll\": true, \"conditions\": []}";

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcClient jdbcClient;

    @TempDir
    Path library;

    @BeforeEach
    void givenLibrary() throws IOException {
        Path heat = Files.createDirectories(library.resolve("filme/Heat"));
        Files.writeString(heat.resolve("Heat.mkv"), "");
        Files.writeString(heat.resolve("Heat.nfo"), """
                <movie><title>Heat</title><year>1995</year><genre>Krimi</genre><playcount>1</playcount>
                <ratings><rating name="imdb" max="10" default="true"><value>8.3</value></rating></ratings>
                <fileinfo><streamdetails><video><codec>h264</codec><width>1920</width><height>1080</height></video>
                <audio><codec>ac3</codec><language>deu</language></audio></streamdetails></fileinfo></movie>""");
        ImageIO.write(new BufferedImage(200, 300, BufferedImage.TYPE_INT_RGB), "jpg",
                heat.resolve("Heat-poster.jpg").toFile());
        Path ronin = Files.createDirectories(library.resolve("filme/Ronin"));
        Files.writeString(ronin.resolve("Ronin.mkv"), "");
        Files.writeString(ronin.resolve("Ronin.nfo"), "<movie><title>Ronin</title><year>1998</year></movie>");
        Path alf = Files.createDirectories(library.resolve("serien/ALF"));
        Files.writeString(alf.resolve("tvshow.nfo"), "<tvshow><title>ALF</title><status>Ended</status></tvshow>");
        Files.writeString(alf.resolve("ALF - S01E01.mkv"), "");
        Files.writeString(alf.resolve("ALF - S01E02.mkv"), "");
        scan("Filme", library.resolve("filme"), "MOVIE");
        scan("Serien", library.resolve("serien"), "TV_SHOW");
    }

    @AfterEach
    void cleanUp() {
        jdbcClient.sql("DELETE FROM media_source").update();
    }

    @Test
    void exportsTheFilteredMoviesAsCsvAndJson() {
        MvcTestResult csv = export(ExportController.MOVIES_EXPORT_PATH, "CSV&lang=de_DE",
                "{\"matchAll\": true, \"conditions\": [{\"field\": \"GENRE\", \"operator\": \"IS\", \"value\": \"Krimi\"}]}");

        assertThat(csv).hasStatusOk().hasContentType("text/csv;charset=UTF-8")
                .hasHeader("Content-Disposition", "attachment; filename=\"kmm-filme-%s.csv\""
                        .formatted(LocalDate.now()));
        String text = new String(csv.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(text.lines()).hasSize(2);
        assertThat(text.lines().toList().get(1)).startsWith("Heat;;1995;Krimi;;;8,3;;ja;1080p;h264;deu;");

        JsonNode json = objectMapper.readTree(export(ExportController.MOVIES_EXPORT_PATH, "JSON", ALL).getResponse()
                .getContentAsByteArray());
        assertThat(json.valueStream().map(movie -> movie.get("title").asString())).containsExactly("Heat", "Ronin");
        assertThat(json.get(0).get("resolution").asString()).isEqualTo("1080p");
        assertThat(json.get(1).get("watched").asBoolean()).isFalse();
    }

    @Test
    void exportsInEnglishWithoutOrWithAnUnsupportedLanguage() {
        for (String format : List.of("CSV", "CSV&lang=en", "CSV&lang=fr")) {
            MvcTestResult csv = export(ExportController.MOVIES_EXPORT_PATH, format, ALL);

            assertThat(csv).hasStatusOk().hasHeader("Content-Disposition",
                    "attachment; filename=\"kmm-movies-%s.csv\"".formatted(LocalDate.now()));
            String text = new String(csv.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
            assertThat(text).as(format).startsWith("\uFEFFTitle,Original title,Year,");
            assertThat(text.lines().toList().get(1)).as(format).startsWith("Heat,,1995,Krimi,,,8.3,,yes,1080p,h264,");
        }
    }

    @Test
    void exportsShowsAndAnHtmlListWithPosters() throws IOException {
        long before = exportFiles();
        JsonNode shows = objectMapper.readTree(export(ExportController.SHOWS_EXPORT_PATH, "JSON", ALL).getResponse()
                .getContentAsByteArray());
        assertThat(shows.get(0).get("title").asString()).isEqualTo("ALF");
        assertThat(shows.get(0).get("episodes").asInt()).isEqualTo(2);
        assertThat(shows.get(0).get("unwatched").asInt()).isEqualTo(2);
        assertThat(shows.get(0).get("seasons").asInt()).isEqualTo(1);

        MvcTestResult html = export(ExportController.MOVIES_EXPORT_PATH, "HTML", ALL);

        assertThat(html).hasStatusOk().hasContentType("application/zip");
        List<String> names = new ArrayList<>();
        String index = "";
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(html.getResponse()
                .getContentAsByteArray()))) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                names.add(entry.getName());
                if (entry.getName().equals("index.html")) {
                    index = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        assertThat(names).hasSize(2).contains("index.html").anyMatch(name -> name.startsWith("posters/"));
        assertThat(index).contains("<td>Heat</td>", "<td>Ronin</td>", "src=\"posters/");
        assertThat(exportFiles()).as("temporary files deleted").isEqualTo(before);
        assertThat(export(ExportController.MOVIES_EXPORT_PATH, "PDF", ALL)).hasStatus(HttpStatus.BAD_REQUEST);
    }

    private MvcTestResult export(String path, String format, String filter) {
        return mockMvc.post().uri(path + "?format=" + format).with(csrf()).contentType(JSON).content(filter)
                .exchange();
    }

    private static long exportFiles() throws IOException {
        try (Stream<Path> files = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return files.filter(file -> file.getFileName().toString().startsWith("kmm-export-")).count();
        }
    }

    private void scan(String name, Path path, String mediaType) {
        MvcTestResult source = mockMvc.post().uri(MediaSourceController.BASE_PATH).with(csrf()).contentType(JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", name, "path", path.toString(),
                        "mediaType", mediaType)))
                .exchange();
        assertThat(source).hasStatus(HttpStatus.CREATED);
        String jobId = read(mockMvc.post().uri(LibraryScanController.SCAN_PATH, read(source).get("id").asLong())
                .with(csrf()).exchange()).get("id").asString();
        await().atMost(TIMEOUT).pollInSameThread()
                .until(() -> read(mockMvc.get().uri(JobController.BASE_PATH + "/" + jobId).exchange()),
                        node -> !"RUNNING".equals(node.get("status").asString()));
    }

    private JsonNode read(MvcTestResult result) {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }
}
