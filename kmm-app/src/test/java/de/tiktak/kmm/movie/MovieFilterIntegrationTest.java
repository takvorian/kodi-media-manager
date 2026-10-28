package de.tiktak.kmm.movie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
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
 * Filter der Filmliste Ende zu Ende gegen die Datenbank, mit technischen Angaben aus den NFOs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser
class MovieFilterIntegrationTest {

    private static final String JSON = "application/json";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private MovieStreamIndexBackfill backfill;

    @TempDir
    Path library;

    @BeforeEach
    void givenLibrary() throws IOException {
        Path filme = Files.createDirectories(library.resolve("filme"));
        givenMovie(filme, "Heat", 1995, "<genre>Krimi</genre><genre>Drama</genre><tag>Favorit</tag>"
                + "<playcount>2</playcount><userrating>9</userrating><mpaa>FSK 16</mpaa>"
                + streams("1920", "1040", "h264", null, "dtshd_ma", "deu", "ac3", "ger"));
        givenMovie(filme, "Matrix", 1999, "<genre>Science-Fiction</genre><set><name>Matrix Filmreihe</name></set>"
                + streams("3840", "1600", "hevc", "hdr10", "truehd", "deu", "ac3", "deu"));
        givenMovie(filme, "Pippi", 1969, "<genre>Kinderfilm</genre><tag>Kinder_50%</tag>"
                + streams("720", "576", "mpeg2video", null, "ac3", "deu", null, null));
        givenMovie(filme, "Ohne Angaben", 2020, "");
        scan(filme);
    }

    @AfterEach
    void cleanUp() {
        jdbcClient.sql("DELETE FROM media_source").update();
        jdbcClient.sql("DELETE FROM movie_set").update();
    }

    @Test
    void filtersByMetadataAndPlayback() {
        assertThat(titles(true, condition("GENRE", "IS", "krimi"))).containsExactly("Heat");
        assertThat(titles(true, condition("GENRE", "IS_NOT", "Krimi"))).containsExactly("Matrix", "Ohne Angaben",
                "Pippi");
        assertThat(titles(true, condition("WATCHED", "IS", "false"))).containsExactly("Matrix", "Ohne Angaben", "Pippi");
        assertThat(titles(true, condition("SET", "IS", "Matrix Filmreihe"))).containsExactly("Matrix");
        assertThat(titles(true, condition("SET", "EMPTY", null))).containsExactly("Heat", "Ohne Angaben", "Pippi");
        assertThat(titles(true, condition("YEAR", "AT_LEAST", "1995"), condition("YEAR", "AT_MOST", "2000")))
                .containsExactly("Heat", "Matrix");
        assertThat(titles(false, condition("TAG", "IS", "Favorit"), condition("MPAA", "EMPTY", null)))
                .containsExactly("Heat", "Matrix", "Ohne Angaben", "Pippi");
        assertThat(titles(true, condition("TITLE", "CONTAINS", "_"))).as("_ ist kein Platzhalter").isEmpty();
        assertThat(titles(true, condition("TAG", "IS", "kinder_50%"))).containsExactly("Pippi");
        assertThat(titles(true, condition("USER_RATING", "AT_LEAST", "8"))).containsExactly("Heat");
        assertThat(titles(true, condition("ADDED_DAYS_AGO", "AT_MOST", "1"))).hasSize(4);
    }

    @Test
    void filtersByStreamDetails() {
        assertThat(titles(true, condition("RESOLUTION", "AT_LEAST", "1080"))).containsExactly("Heat", "Matrix");
        assertThat(titles(true, condition("RESOLUTION", "AT_MOST", "720"))).containsExactly("Pippi");
        assertThat(titles(true, condition("VIDEO_CODEC", "IS", "HEVC"))).containsExactly("Matrix");
        assertThat(titles(true, condition("HDR", "NOT_EMPTY", null))).containsExactly("Matrix");
        assertThat(titles(true, condition("AUDIO_CODEC", "IS", "ac3"))).containsExactly("Heat", "Matrix", "Pippi");
        assertThat(titles(true, condition("AUDIO_LANGUAGE", "IS_NOT", "eng"))).containsExactly("Ohne Angaben",
                "Pippi");
        assertThat(titles(true, condition("SUBTITLE_LANGUAGE", "IS", "ger"))).containsExactly("Heat");
        assertThat(titles(true, condition("STREAM_DETAILS", "EMPTY", null))).containsExactly("Ohne Angaben");
    }

    @Test
    void combinesSearchSortingAndPaging() {
        JsonNode page = query("{\"search\": \"a\", \"matchAll\": true, \"conditions\": []}", "sort=title,desc&size=2");

        assertThat(page.get("content").valueStream().map(movie -> movie.get("title").asString()))
                .containsExactly("Ohne Angaben", "Matrix");
        assertThat(page.get("totalElements").asInt()).isEqualTo(3);
    }

    @Test
    void offersTheExistingValuesForSelection() {
        JsonNode values = read(mockMvc.get().uri(MovieController.FILTER_VALUES_PATH).exchange());

        assertThat(values.get("genres").valueStream().map(JsonNode::asString))
                .containsExactly("Drama", "Kinderfilm", "Krimi", "Science-Fiction");
        assertThat(values.get("sets").valueStream().map(JsonNode::asString)).containsExactly("Matrix Filmreihe");
        assertThat(values.get("resolutions").valueStream().map(JsonNode::asInt)).containsExactly(576, 1080, 2160);
        assertThat(values.get("audioCodecs").valueStream().map(JsonNode::asString))
                .containsExactly("ac3", "dtshd_ma", "truehd");
        assertThat(values.get("subtitleLanguages").valueStream().map(JsonNode::asString)).containsExactly("deu", "ger");
    }

    @Test
    void rejectsConditionsThatDoNotFitTheirField() {
        assertThat(post("{\"matchAll\": true, \"conditions\": [{\"field\": \"WATCHED\", \"operator\": \"AT_LEAST\","
                + " \"value\": \"1\"}]}", "")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(post("{\"matchAll\": true, \"conditions\": [{\"field\": \"YEAR\", \"operator\": \"IS\","
                + " \"value\": \"neunzehn\"}]}", "")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(post("{\"matchAll\": true, \"conditions\": [{\"field\": \"GENRE\", \"operator\": \"IS\"}]}", ""))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void indexesMoviesWhoseStreamDetailsPredateTheIndex() {
        jdbcClient.sql("UPDATE movie SET audio_codecs = NULL, video_resolution = NULL").update();
        assertThat(titles(true, condition("RESOLUTION", "AT_LEAST", "1080"))).isEmpty();

        backfill.indexMissing();

        assertThat(titles(true, condition("RESOLUTION", "AT_LEAST", "1080"))).containsExactly("Heat", "Matrix");
    }

    @SafeVarargs
    private List<String> titles(boolean matchAll, Map<String, String>... conditions) {
        String body = objectMapper.writeValueAsString(Map.of("matchAll", matchAll, "conditions", List.of(conditions)));
        return query(body, "sort=title").get("content").valueStream().map(movie -> movie.get("title").asString())
                .toList();
    }

    private static Map<String, String> condition(String field, String operator, String value) {
        return value == null ? Map.of("field", field, "operator", operator)
                : Map.of("field", field, "operator", operator, "value", value);
    }

    private JsonNode query(String body, String parameters) {
        MvcTestResult result = post(body, parameters);
        assertThat(result).hasStatusOk();
        return read(result);
    }

    private MvcTestResult post(String body, String parameters) {
        return mockMvc.post().uri(MovieController.QUERY_PATH + "?" + parameters).with(csrf()).contentType(JSON)
                .content(body).exchange();
    }

    private static String streams(String width, String height, String videoCodec, String hdr, String audioCodec,
            String audioLanguage, String secondCodec, String subtitle) {
        String second = secondCodec == null ? "" : "<audio><codec>%s</codec><language>eng</language></audio>"
                .formatted(secondCodec);
        String subtitles = subtitle == null ? "" : "<subtitle><language>%s</language></subtitle>".formatted(subtitle);
        String hdrType = hdr == null ? "" : "<hdrtype>%s</hdrtype>".formatted(hdr);
        return ("<fileinfo><streamdetails><video><codec>%s</codec><width>%s</width><height>%s</height>%s</video>"
                + "<audio><codec>%s</codec><language>%s</language></audio>%s%s</streamdetails></fileinfo>")
                .formatted(videoCodec, width, height, hdrType, audioCodec, audioLanguage, second, subtitles);
    }

    private void givenMovie(Path filme, String title, int year, String extra) throws IOException {
        Path folder = Files.createDirectories(filme.resolve(title));
        Files.writeString(folder.resolve(title + ".mkv"), "");
        Files.writeString(folder.resolve(title + ".nfo"),
                "<movie><title>%s</title><year>%d</year>%s</movie>".formatted(title, year, extra));
    }

    private void scan(Path path) {
        MvcTestResult source = mockMvc.post().uri(MediaSourceController.BASE_PATH).with(csrf()).contentType(JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Filme", "path", path.toString(),
                        "mediaType", "MOVIE")))
                .exchange();
        assertThat(source).hasStatus(HttpStatus.CREATED);
        MvcTestResult started = mockMvc.post().uri(LibraryScanController.SCAN_PATH, read(source).get("id").asLong())
                .with(csrf()).exchange();
        String jobId = read(started).get("id").asString();
        await().atMost(TIMEOUT).pollInSameThread()
                .until(() -> read(mockMvc.get().uri(JobController.BASE_PATH + "/" + jobId).exchange()),
                        node -> !"RUNNING".equals(node.get("status").asString()));
    }

    private JsonNode read(MvcTestResult result) {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }
}
