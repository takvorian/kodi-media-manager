package de.tiktak.kmm.tvshow;

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
 * Filter der Serienliste Ende zu Ende, auch über die Episoden.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser
class TvShowFilterIntegrationTest {

    private static final String JSON = "application/json";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String ALF_TRAILER = "plugin://plugin.video.youtube/?action=play_video&videoid=alfTrailer1";
    private static final String LOST_TRAILER = "https://example.org/lost-trailer.mp4";

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private EpisodeStreamIndexBackfill backfill;

    @TempDir
    Path library;

    @BeforeEach
    void givenLibrary() throws IOException {
        Path serien = Files.createDirectories(library.resolve("serien"));
        givenShow(serien, "ALF", 1986, "<genre>Comedy</genre><status>Ended</status><tag>Kult</tag><trailer>"
                + ALF_TRAILER.replace("&", "&amp;") + "</trailer>");
        givenEpisode(serien, "ALF", 1, 1, "<playcount>1</playcount>" + streams("720", "576", "mpeg2video", "ac3", "deu"));
        givenEpisode(serien, "ALF", 1, 2, "<playcount>2</playcount>" + streams("720", "576", "mpeg2video", "ac3", "eng"));
        givenShow(serien, "Lost", 2004, "<genre>Drama</genre><status>Ended</status>");
        givenEpisode(serien, "Lost", 1, 1, "<playcount>1</playcount>" + streams("1920", "1080", "avc1", "mp4a-40-2",
                "eng"));
        givenEpisode(serien, "Lost", 1, 2, "");
        givenShow(serien, "Neu", 2024, "<genre>Drama</genre><status>Continuing</status>");
        givenEpisode(serien, "Neu", 1, 1, "<playcount>1</playcount>" + streams("1280", "720", "hevc", "eac3", "deu"));
        scan(serien);
    }

    @AfterEach
    void cleanUp() {
        jdbcClient.sql("DELETE FROM media_source").update();
    }

    @Test
    void filtersByShowData() {
        assertThat(titles(true, condition("GENRE", "IS", "drama"))).containsExactly("Lost", "Neu");
        assertThat(titles(true, condition("STATUS", "IS", "Continuing"))).containsExactly("Neu");
        assertThat(titles(true, condition("TAG", "NOT_EMPTY", null))).containsExactly("ALF");
        assertThat(titles(true, condition("YEAR", "AT_LEAST", "2000"), condition("EPISODES", "AT_LEAST", "2")))
                .containsExactly("Lost");
        assertThat(titles(false, condition("EPISODES", "IS", "1"), condition("TAG", "IS", "Kult")))
                .containsExactly("ALF", "Neu");
    }

    @Test
    void filtersByTheirEpisodes() {
        assertThat(titles(true, condition("UNWATCHED_EPISODES", "IS", "true"))).containsExactly("Lost");
        assertThat(titles(true, condition("UNWATCHED_EPISODES", "IS", "false"))).containsExactly("ALF", "Neu");
        assertThat(titles(true, condition("EPISODE_RESOLUTION", "AT_MOST", "576"))).containsExactly("ALF");
        assertThat(titles(true, condition("EPISODE_VIDEO_CODEC", "IS", "h264"))).containsExactly("Lost");
        assertThat(titles(true, condition("EPISODE_AUDIO_CODEC", "IS", "aac"))).containsExactly("Lost");
        assertThat(titles(true, condition("EPISODE_AUDIO_LANGUAGE", "IS_NOT", "eng"))).containsExactly("Neu");
        assertThat(titles(true, condition("EPISODE_STREAM_DETAILS", "EMPTY", null))).containsExactly("Lost");
    }

    @Test
    void offersValuesReturnsIdsAndRejectsInvalidConditions() {
        JsonNode values = read(mockMvc.get().uri(TvShowController.FILTER_VALUES_PATH).exchange());
        assertThat(values.get("statuses").valueStream().map(JsonNode::asString)).containsExactly("Continuing", "Ended");
        assertThat(values.get("videoCodecs").valueStream().map(JsonNode::asString)).containsExactly("h264", "hevc",
                "mpeg2video");
        assertThat(values.get("resolutions").valueStream().map(JsonNode::asInt)).containsExactly(576, 720, 1080);

        JsonNode ids = read(post(TvShowController.QUERY_IDS_PATH,
                "{\"matchAll\": true, \"conditions\": [{\"field\": \"GENRE\", \"operator\": \"IS\", \"value\": \"Drama\"}]}"));
        assertThat(ids.size()).isEqualTo(2);
        assertThat(post(TvShowController.QUERY_PATH,
                "{\"matchAll\": true, \"conditions\": [{\"field\": \"EPISODES\", \"operator\": \"EMPTY\"}]}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void editsTheFilteredShowsTogether() throws IOException {
        JsonNode ids = read(post(TvShowController.QUERY_IDS_PATH,
                "{\"matchAll\": true, \"conditions\": [{\"field\": \"GENRE\", \"operator\": \"IS\", \"value\": \"Drama\"}]}"));

        MvcTestResult started = post(TvShowController.BATCH_PATH, ("{\"showIds\": %s, \"genres\": {\"add\": [\"Mystery\"]},"
                + " \"tags\": {\"add\": [\"Neu\"]}, \"status\": {\"value\": \"Ended\"}}").formatted(ids));
        assertThat(started).hasStatus(HttpStatus.ACCEPTED);
        String jobId = read(started).get("id").asString();
        JsonNode job = await().atMost(TIMEOUT).pollInSameThread()
                .until(() -> read(mockMvc.get().uri(JobController.BASE_PATH + "/" + jobId).exchange()),
                        node -> !"RUNNING".equals(node.get("status").asString()));

        assertThat(job.get("result").get("changed").asInt()).as(job.toString()).isEqualTo(2);
        assertThat(Files.readString(library.resolve("serien/Neu/tvshow.nfo")))
                .contains("<genre>Drama</genre>", "<genre>Mystery</genre>", "<tag>Neu</tag>", "<status>Ended</status>");
        assertThat(titles(true, condition("STATUS", "IS", "Continuing"))).isEmpty();
        assertThat(post(TvShowController.BATCH_PATH, "{\"showIds\": [], \"tags\": {\"add\": [\"X\"]}}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void filtersByTrailerAndChangesItInTheNfo() throws IOException {
        assertThat(titles(true, condition("TRAILER", "NOT_EMPTY", null))).containsExactly("ALF");
        assertThat(titles(true, condition("TRAILER", "EMPTY", null))).containsExactly("Lost", "Neu");
        long lost = showId("Lost");
        Path lostNfo = library.resolve("serien/Lost/tvshow.nfo");

        MvcTestResult applied = mockMvc.put().uri(TvShowTrailerController.TRAILER_PATH, lost).with(csrf())
                .contentType(JSON).content("{\"url\": \"%s\"}".formatted(LOST_TRAILER)).exchange();
        assertThat(applied).hasStatusOk();
        assertThat(read(applied).get("trailer").asString()).isEqualTo(LOST_TRAILER);
        assertThat(Files.readString(lostNfo)).contains("<trailer>" + LOST_TRAILER + "</trailer>",
                "<title>Lost</title>");
        assertThat(titles(true, condition("TRAILER", "EMPTY", null))).containsExactly("Neu");

        assertThat(mockMvc.put().uri(TvShowTrailerController.TRAILER_PATH, lost).with(csrf()).contentType(JSON)
                .content("{\"url\": \"ftp://nas/lost.mkv\"}").exchange()).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(mockMvc.get().uri(TvShowTrailerController.CANDIDATES_PATH, lost).exchange())
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);

        MvcTestResult removed = mockMvc.delete().uri(TvShowTrailerController.TRAILER_PATH, lost).with(csrf())
                .exchange();
        assertThat(removed).hasStatusOk();
        assertThat(read(removed).get("trailer").isNull()).isTrue();
        assertThat(Files.readString(lostNfo)).doesNotContain("<trailer").contains("<title>Lost</title>");
    }

    @Test
    void readsTheNfoOfAnEmberShowFirstAndChangesOnlyItsTrailerLine() throws IOException {
        Path nfo = library.resolve("serien/Neu/tvshow.nfo");
        String ember = String.join("\r\n", "<?xml version=\"1.0\" encoding=\"utf-8\"?>", "<tvshow>",
                "  <title>Neu</title>", "  <mpaa>Germany:16</mpaa>", "  <actor>", "    <name>Ricky Gervais</name>",
                "    <tmdbid>17835</tmdbid>", "  </actor>", "</tvshow>");
        Files.writeString(nfo, ember);
        long neu = showId("Neu");
        jdbcClient.sql("UPDATE tv_show SET mpaa = 'FSK 16', nfo_read = FALSE, nfo_modified_at = :modified "
                + "WHERE id = :id").param("modified", Files.getLastModifiedTime(nfo).toInstant())
                .param("id", neu).update();

        assertThat(mockMvc.put().uri(TvShowTrailerController.TRAILER_PATH, neu).with(csrf()).contentType(JSON)
                .content("{\"url\": \"%s\"}".formatted(LOST_TRAILER)).exchange()).hasStatusOk();

        assertThat(Files.readString(nfo)).isEqualTo(ember.replace("</tvshow>",
                "  <trailer>" + LOST_TRAILER + "</trailer>\r\n</tvshow>"));
        assertThat(jdbcClient.sql("SELECT mpaa FROM tv_show WHERE id = :id").param("id", neu).query(String.class)
                .single()).as("aus der NFO eingelesen").isEqualTo("Germany:16");
    }

    @Test
    void fillsMissingTrailersWithoutTouchingExistingOnes() throws IOException {
        Path alfNfo = library.resolve("serien/ALF/tvshow.nfo");
        String before = Files.readString(alfNfo);

        MvcTestResult started = post(TvShowTrailerController.FILL_PATH,
                "{\"showIds\": [%d, %d, %d]}".formatted(showId("ALF"), showId("Lost"), showId("ALF")));
        assertThat(started).hasStatus(HttpStatus.ACCEPTED);
        assertThat(read(started).get("title").asString()).isEqualTo("Fehlende Trailer für 2 Serien holen");
        String jobId = read(started).get("id").asString();
        JsonNode job = await().atMost(TIMEOUT).pollInSameThread()
                .until(() -> read(mockMvc.get().uri(JobController.BASE_PATH + "/" + jobId).exchange()),
                        node -> !"RUNNING".equals(node.get("status").asString()));

        assertThat(job.get("result").get("changed").asInt()).as(job.toString()).isZero();
        assertThat(job.get("result").get("unchanged").asInt()).as(job.toString()).isEqualTo(2);
        assertThat(Files.readString(alfNfo)).isEqualTo(before);
        assertThat(post(TvShowTrailerController.FILL_PATH, "{\"showIds\": []}")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void keepsTheTrailerWhenEditingTheShow() throws IOException {
        MvcTestResult started = post(TvShowController.BATCH_PATH,
                "{\"showIds\": [%d], \"tags\": {\"add\": [\"Neu\"]}}".formatted(showId("ALF")));
        String jobId = read(started).get("id").asString();
        await().atMost(TIMEOUT).pollInSameThread()
                .until(() -> read(mockMvc.get().uri(JobController.BASE_PATH + "/" + jobId).exchange()),
                        node -> !"RUNNING".equals(node.get("status").asString()));

        assertThat(Files.readString(library.resolve("serien/ALF/tvshow.nfo")))
                .contains("<tag>Neu</tag>", "<trailer>" + ALF_TRAILER.replace("&", "&amp;") + "</trailer>");
    }

    @Test
    void indexesEpisodesWhoseStreamDetailsPredateTheIndex() {
        jdbcClient.sql("UPDATE episode SET audio_codecs = NULL, video_codec = NULL").update();
        assertThat(titles(true, condition("EPISODE_VIDEO_CODEC", "IS", "h264"))).isEmpty();

        backfill.indexMissing();

        assertThat(titles(true, condition("EPISODE_VIDEO_CODEC", "IS", "h264"))).containsExactly("Lost");
    }

    @SafeVarargs
    private List<String> titles(boolean matchAll, Map<String, String>... conditions) {
        MvcTestResult result = post(TvShowController.QUERY_PATH + "?sort=title",
                objectMapper.writeValueAsString(Map.of("matchAll", matchAll, "conditions", List.of(conditions))));
        assertThat(result).hasStatusOk();
        return read(result).get("content").valueStream().map(show -> show.get("title").asString()).toList();
    }

    private long showId(String title) {
        JsonNode ids = read(post(TvShowController.QUERY_IDS_PATH, objectMapper.writeValueAsString(Map.of(
                "matchAll", true, "conditions", List.of(condition("TITLE", "IS", title))))));
        assertThat(ids.size()).isEqualTo(1);
        return ids.get(0).asLong();
    }

    private static Map<String, String> condition(String field, String operator, String value) {
        return value == null ? Map.of("field", field, "operator", operator)
                : Map.of("field", field, "operator", operator, "value", value);
    }

    private MvcTestResult post(String uri, String body) {
        return mockMvc.post().uri(uri).with(csrf()).contentType(JSON).content(body).exchange();
    }

    private static String streams(String width, String height, String videoCodec, String audioCodec,
            String language) {
        return ("<fileinfo><streamdetails><video><codec>%s</codec><width>%s</width><height>%s</height></video>"
                + "<audio><codec>%s</codec><language>%s</language></audio></streamdetails></fileinfo>")
                .formatted(videoCodec, width, height, audioCodec, language);
    }

    private static void givenShow(Path serien, String title, int year, String extra) throws IOException {
        Path folder = Files.createDirectories(serien.resolve(title));
        Files.writeString(folder.resolve("tvshow.nfo"),
                "<tvshow><title>%s</title><premiered>%d-09-22</premiered>%s</tvshow>".formatted(title, year, extra));
    }

    private static void givenEpisode(Path serien, String show, int season, int episode, String extra)
            throws IOException {
        String name = "%s - S%02dE%02d".formatted(show, season, episode);
        Path folder = serien.resolve(show);
        Files.writeString(folder.resolve(name + ".mkv"), "");
        Files.writeString(folder.resolve(name + ".nfo"),
                "<episodedetails><title>Folge %d</title><season>%d</season><episode>%d</episode>%s</episodedetails>"
                        .formatted(episode, season, episode, extra));
    }

    private void scan(Path path) {
        MvcTestResult source = mockMvc.post().uri(MediaSourceController.BASE_PATH).with(csrf()).contentType(JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Serien", "path", path.toString(),
                        "mediaType", "TV_SHOW")))
                .exchange();
        assertThat(source).hasStatus(HttpStatus.CREATED);
        String jobId = read(mockMvc.post().uri(LibraryScanController.SCAN_PATH, read(source).get("id").asLong())
                .with(csrf()).exchange()).get("id").asString();
        JsonNode job = await().atMost(TIMEOUT).pollInSameThread()
                .until(() -> read(mockMvc.get().uri(JobController.BASE_PATH + "/" + jobId).exchange()),
                        node -> !"RUNNING".equals(node.get("status").asString()));
        assertThat(job.get("status").asString()).as(job.toString()).isEqualTo("SUCCEEDED");
    }

    private JsonNode read(MvcTestResult result) {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }
}
