package de.tiktak.kmm.plugin.youtube;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.PluginContext;
import de.tiktak.kmm.plugin.api.ScrapeRequest;
import de.tiktak.kmm.plugin.api.ScraperException;
import de.tiktak.kmm.plugin.api.ThemeCandidate;

class YouTubeThemeScraperTest {

    private static final String SEARCH_OUTPUT = """
            {"id": "KTu8iDynwNc", "title": "ALF Intro (HD)", "channel": "Retro TV", "duration": 31}
            {"id": "longEpisode1", "title": "ALF – ganze Folge", "channel": "", "duration": 1500}
            no JSON
            {"id": "x", "title": "zu kurze ID"}
            {"id": "noDuration01", "title": "ALF Titellied"}
            """;
    private static final URI ALF_INTRO = URI.create("https://www.youtube.com/watch?v=KTu8iDynwNc");

    private final FakeRunner runner = new FakeRunner();
    private YouTubeThemeScraper scraper;

    @TempDir
    Path directory;

    @BeforeEach
    void setUp() {
        scraper = new YouTubeThemeScraper(runner);
        scraper.initialize(context(Map.of()));
    }

    @Test
    void searchesVideosAndSkipsLongOnes() {
        runner.output = SEARCH_OUTPUT;

        List<ThemeCandidate> themes = scraper.findThemes(new ScrapeRequest(MediaType.TV_SHOW, " ALF ", 1986,
                Map.of()));

        assertThat(themes).containsExactly(
                new ThemeCandidate("ALF Intro (HD) (Retro TV)", null, ALF_INTRO, null, ALF_INTRO, 31),
                new ThemeCandidate("ALF Titellied", null, URI.create("https://www.youtube.com/watch?v=noDuration01"),
                        null, URI.create("https://www.youtube.com/watch?v=noDuration01"), null));
        assertThat(runner.commands.getFirst()).containsExactly("yt-dlp", "--flat-playlist", "--dump-json",
                "--no-warnings", "ytsearch6:ALF 1986 intro");
    }

    @Test
    void searchesMoviesForTheMainThemeAndIgnoresOtherMedia() {
        scraper.initialize(context(Map.of("yt-dlp", "C:/tools/yt-dlp.exe", "max-results", "99")));

        scraper.findThemes(new ScrapeRequest(MediaType.MOVIE, "Heat", null, Map.of()));

        assertThat(runner.commands.getFirst()).startsWith("C:/tools/yt-dlp.exe")
                .endsWith("ytsearch20:Heat main theme");
        assertThat(scraper.findThemes(new ScrapeRequest(MediaType.TV_EPISODE, "Pilot", null, Map.of()))).isEmpty();
        assertThat(scraper.supportedMediaTypes()).containsExactlyInAnyOrder(MediaType.MOVIE, MediaType.TV_SHOW);
        assertThat(scraper.id()).isEqualTo("youtube");
        assertThat(scraper.displayName()).isEqualTo("YouTube");
    }

    @Test
    void downloadsTheAudioAsMp3() throws IOException {
        scraper.initialize(context(Map.of("ffmpeg-location", "C:/ffmpeg/bin", "max-results", "kaputt")));
        runner.createdFile = "ID3 Musik";
        Path target = directory.resolve("theme.mp3.kmm-tmp");

        scraper.download(candidate(ALF_INTRO), target);

        assertThat(Files.readString(target)).isEqualTo("ID3 Musik");
        List<String> command = runner.commands.getFirst();
        assertThat(command).contains("--extract-audio", "--audio-format", "mp3", "--ffmpeg-location", "C:/ffmpeg/bin")
                .endsWith(ALF_INTRO.toString());
        Path output = Path.of(command.get(command.indexOf("--output") + 1));
        assertThat(output.getParent()).doesNotExist();
    }

    @Test
    void refusesOtherAddressesAndReportsFailures() {
        Path target = directory.resolve("theme.mp3");
        for (String source : new String[] {"https://example.org/watch?v=KTu8iDynwNc",
                "https://www.youtube.com/playlist?list=PL1234567", "https://www.youtube.com/watch?v=;rm",
                "https://www.youtube.com/watch"}) {
            assertThatThrownBy(() -> scraper.download(candidate(URI.create(source)), target))
                    .as(source).isInstanceOfSatisfying(ScraperException.class, e -> assertThat(e.localizedText())
                            .isEqualTo(LocalizedText.of("scraper.youtube.notAVideo").with("source", source)));
        }

        assertThatThrownBy(() -> scraper.download(candidate(ALF_INTRO), target))
                .isInstanceOfSatisfying(ScraperException.class, e -> assertThat(e.localizedText())
                        .isEqualTo(LocalizedText.of("scraper.youtube.noMp3")));

        runner.exitCode = 1;
        runner.errors = "ERROR: Video unavailable";
        assertThatThrownBy(() -> scraper.download(candidate(ALF_INTRO), target))
                .isInstanceOfSatisfying(ScraperException.class, e -> assertThat(e.localizedText())
                        .isEqualTo(LocalizedText.of("scraper.youtube.ytDlpFailed")
                                .with("errors", "ERROR: Video unavailable")));

        runner.failure = new IOException("program not found");
        assertThatThrownBy(() -> scraper.findThemes(new ScrapeRequest(MediaType.MOVIE, "Heat", null, Map.of())))
                .isInstanceOfSatisfying(ScraperException.class, e -> assertThat(e.localizedText())
                        .isEqualTo(LocalizedText.of("scraper.youtube.ytDlpNotRunning")
                                .with("reason", "program not found")));
        assertThat(target).doesNotExist();
    }

    @Test
    void reportsAMissingYtDlpOnce() {
        assertThat(new YouTubeThemeScraper(runner).missingConfiguration())
                .contains(LocalizedText.of("scraper.youtube.notInitialized"));
        assertThatThrownBy(() -> new YouTubeThemeScraper(runner).findThemes(
                new ScrapeRequest(MediaType.MOVIE, "Heat", null, Map.of()))).isInstanceOf(IllegalStateException.class);

        assertThat(scraper.missingConfiguration()).isEmpty();

        runner.failure = new IOException("not found");
        scraper.initialize(context(Map.of()));
        assertThat(scraper.missingConfiguration()).hasValueSatisfying(message ->
                assertThat(message.key()).isEqualTo("scraper.youtube.ytDlpNotFound"));
        runner.failure = null;
        assertThat(scraper.missingConfiguration()).as("the result holds until the next start").isPresent();
    }

    @Test
    void extractsOnlyValidVideoIds() {
        assertThat(YouTubeThemeScraper.videoId(URI.create("https://youtube.com/watch?t=3&v=KTu8iDynwNc")))
                .contains("KTu8iDynwNc");
        assertThat(YouTubeThemeScraper.videoId(URI.create("file:///watch?v=KTu8iDynwNc"))).isEmpty();
    }

    private static ThemeCandidate candidate(URI source) {
        return new ThemeCandidate("ALF Intro", null, source, null, source, null);
    }

    private static PluginContext context(Map<String, String> settings) {
        return new PluginContext() {
            @Override
            public Optional<String> setting(String name) {
                return Optional.ofNullable(settings.get(name));
            }

            @Override
            public java.net.http.HttpClient httpClient() {
                throw new UnsupportedOperationException();
            }

            @Override
            public String userAgent() {
                return "KMM-Test";
            }
        };
    }

    /** yt-dlp stub: returns fixed outputs and creates the expected file when downloading. */
    private static final class FakeRunner implements CommandRunner {

        private final List<List<String>> commands = new ArrayList<>();
        private String output = "";
        private String errors = "";
        private int exitCode;
        private String createdFile;
        private IOException failure;

        @Override
        public Result run(List<String> command, Duration timeout) throws IOException {
            if (failure != null) {
                throw failure;
            }
            if (!command.contains("--version")) {
                commands.add(command);
            }
            int outputIndex = command.indexOf("--output");
            if (createdFile != null && outputIndex >= 0) {
                Files.writeString(Path.of(command.get(outputIndex + 1).replace("%(ext)s", "mp3")), createdFile);
            }
            return new Result(exitCode, output, errors);
        }
    }
}
