package de.tiktak.kmm.plugin.youtube;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.pf4j.Extension;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.PluginContext;
import de.tiktak.kmm.plugin.api.ScrapeRequest;
import de.tiktak.kmm.plugin.api.ScraperException;
import de.tiktak.kmm.plugin.api.ThemeCandidate;
import de.tiktak.kmm.plugin.api.ThemeScraper;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Theme music from YouTube, like Ember's YouTube theme scraper: search and download via yt-dlp, conversion to MP3 via
 * ffmpeg. The results are videos with titles of their own; that is why KMM never applies them automatically.
 */
@Extension
public class YouTubeThemeScraper implements ThemeScraper {

    static final String ID = "youtube";
    /** Theme music is rarely longer; longer videos are mostly whole episodes or soundtracks. */
    static final int MAX_DURATION_SECONDS = 600;
    private static final Duration SEARCH_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration VERSION_TIMEOUT = Duration.ofSeconds(15);
    private static final String WATCH = "https://www.youtube.com/watch?v=";
    private static final Set<String> YOUTUBE_HOSTS = Set.of("www.youtube.com", "youtube.com", "m.youtube.com");
    private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{6,20}");
    private static final String DOWNLOAD_NAME = "theme";
    private static final String MP3 = ".mp3";
    private static final int MAX_ERROR_LENGTH = 300;

    private final CommandRunner runner;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private YouTubeSettings settings;
    private Boolean ytDlpAvailable;

    public YouTubeThemeScraper() {
        this(new ProcessCommandRunner());
    }

    YouTubeThemeScraper(CommandRunner runner) {
        this.runner = runner;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "YouTube";
    }

    @Override
    public Set<MediaType> supportedMediaTypes() {
        return Set.of(MediaType.MOVIE, MediaType.TV_SHOW);
    }

    @Override
    public void initialize(PluginContext context) {
        settings = YouTubeSettings.from(context);
        ytDlpAvailable = null;
    }

    /** Checks once whether yt-dlp runs; after that the result holds until the next start. */
    @Override
    public synchronized Optional<LocalizedText> missingConfiguration() {
        if (settings == null) {
            return Optional.of(LocalizedText.of("scraper.youtube.notInitialized"));
        }
        if (ytDlpAvailable == null) {
            ytDlpAvailable = ytDlpRuns();
        }
        return ytDlpAvailable ? Optional.empty()
                : Optional.of(LocalizedText.of("scraper.youtube.ytDlpNotFound").with("command", settings.ytDlp()));
    }

    @Override
    public List<ThemeCandidate> findThemes(ScrapeRequest request) {
        requireInitialized();
        if (!supports(request.mediaType())) {
            return List.of();
        }
        CommandRunner.Result result = run(List.of(settings.ytDlp(), "--flat-playlist", "--dump-json",
                "--no-warnings", "ytsearch%d:%s".formatted(settings.maxResults(), query(request))), SEARCH_TIMEOUT);
        return result.output().lines()
                .filter(line -> !line.isBlank())
                .map(this::parse)
                .flatMap(Optional::stream)
                .filter(video -> video.duration() == null || video.duration() <= MAX_DURATION_SECONDS)
                .map(YouTubeThemeScraper::toCandidate)
                .toList();
    }

    @Override
    public void download(ThemeCandidate candidate, Path target) {
        requireInitialized();
        String videoId = videoId(candidate.source())
                .orElseThrow(() -> new ScraperException(LocalizedText.of("scraper.youtube.notAVideo")
                        .with("source", candidate.source())));
        Path directory = createTempDirectory();
        try {
            List<String> command = new ArrayList<>(List.of(settings.ytDlp(), "--no-playlist", "--no-warnings",
                    "--extract-audio", "--audio-format", "mp3", "--max-filesize", "30M",
                    "--output", directory.resolve(DOWNLOAD_NAME + ".%(ext)s").toString()));
            if (settings.hasFfmpegLocation()) {
                command.addAll(List.of("--ffmpeg-location", settings.ffmpegLocation()));
            }
            command.add(WATCH + videoId);
            run(command, DOWNLOAD_TIMEOUT);
            Path mp3 = directory.resolve(DOWNLOAD_NAME + MP3);
            if (!Files.isRegularFile(mp3)) {
                throw new ScraperException(LocalizedText.of("scraper.youtube.noMp3"));
            }
            Files.move(mp3, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ScraperException(LocalizedText.of("scraper.youtube.saveFailed")
                    .with("reason", e.getMessage()), e);
        } finally {
            deleteRecursively(directory);
        }
    }

    /** On YouTube TV show themes are mostly called "Intro", movie themes "Main Theme". */
    private static String query(ScrapeRequest request) {
        String suffix = request.mediaType() == MediaType.TV_SHOW ? "intro" : "main theme";
        return Stream.of(request.title().strip(), request.year() == null ? null : request.year().toString(), suffix)
                .filter(part -> part != null)
                .reduce((first, second) -> first + " " + second)
                .orElseThrow();
    }

    private Optional<Video> parse(String line) {
        try {
            JsonNode node = objectMapper.readTree(line);
            String id = node.path("id").asString("");
            if (!VIDEO_ID.matcher(id).matches()) {
                return Optional.empty();
            }
            int duration = node.path("duration").asInt(0);
            return Optional.of(new Video(id, node.path("title").asString(id), node.path("channel").asString(""),
                    duration > 0 ? duration : null));
        } catch (JacksonException e) {
            return Optional.empty();
        }
    }

    /** No {@code subject}: videos never carry the title of the TV show itself, so they never count as a safe match. */
    private static ThemeCandidate toCandidate(Video video) {
        URI watch = URI.create(WATCH + video.id());
        String name = video.channel().isEmpty() ? video.title() : "%s (%s)".formatted(video.title(), video.channel());
        return new ThemeCandidate(name, null, watch, null, watch, video.duration());
    }

    static Optional<String> videoId(URI source) {
        if (source.getHost() == null || !YOUTUBE_HOSTS.contains(source.getHost().toLowerCase())
                || !"/watch".equals(source.getPath()) || source.getQuery() == null) {
            return Optional.empty();
        }
        return Stream.of(source.getQuery().split("&"))
                .filter(parameter -> parameter.startsWith("v="))
                .map(parameter -> parameter.substring(2))
                .filter(id -> VIDEO_ID.matcher(id).matches())
                .findFirst();
    }

    private CommandRunner.Result run(List<String> command, Duration timeout) {
        try {
            CommandRunner.Result result = runner.run(command, timeout);
            if (!result.succeeded()) {
                throw new ScraperException(LocalizedText.of("scraper.youtube.ytDlpFailed")
                        .with("errors", shorten(result.errors())));
            }
            return result;
        } catch (IOException e) {
            throw new ScraperException(LocalizedText.of("scraper.youtube.ytDlpNotRunning")
                    .with("reason", e.getMessage()), e);
        }
    }

    private boolean ytDlpRuns() {
        try {
            return runner.run(List.of(settings.ytDlp(), "--version"), VERSION_TIMEOUT).succeeded();
        } catch (IOException e) {
            return false;
        }
    }

    private static String shorten(String errors) {
        String text = errors.strip();
        return text.length() <= MAX_ERROR_LENGTH ? text : text.substring(0, MAX_ERROR_LENGTH) + " …";
    }

    private static Path createTempDirectory() {
        try {
            return Files.createTempDirectory("kmm-youtube-");
        } catch (IOException e) {
            throw new ScraperException(LocalizedText.of("scraper.youtube.noTempDirectory")
                    .with("reason", e.getMessage()), e);
        }
    }

    private static void deleteRecursively(Path directory) {
        try (Stream<Path> files = Files.walk(directory)) {
            files.sorted(Comparator.reverseOrder()).forEach(file -> file.toFile().delete());
        } catch (IOException e) {
            directory.toFile().deleteOnExit();
        }
    }

    private void requireInitialized() {
        if (settings == null) {
            throw new IllegalStateException("YouTube scraper has not been initialized");
        }
    }

    private record Video(String id, String title, String channel, Integer duration) {
    }
}
