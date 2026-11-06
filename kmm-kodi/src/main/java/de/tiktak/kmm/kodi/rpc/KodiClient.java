package de.tiktak.kmm.kodi.rpc;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import de.tiktak.kmm.i18n.LocalizedText;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Talks to the JSON-RPC interface of Kodi (Kodi 19 and later) over HTTP. Errors are translated into
 * understandable {@link KodiException}s.
 */
@Slf4j
public class KodiClient {

    private static final String JSON_RPC_VERSION = "2.0";
    private static final int OK = 200;
    private static final int UNAUTHORIZED = 401;
    private static final String DEFAULT_NAME = "Kodi";
    private static final String IS_SCANNING_VIDEO = "Library.IsScanningVideo";
    /** Entries per request when reading whole sources; keeps responses over slow connections small. */
    static final int PAGE_SIZE = 500;
    private static final DateTimeFormatter KODI_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final HttpClient httpClient;
    private final Duration requestTimeout;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final AtomicLong requestIds = new AtomicLong();

    public KodiClient(HttpClient httpClient, Duration requestTimeout) {
        this.httpClient = httpClient;
        this.requestTimeout = requestTimeout;
    }

    /** Checks the connection and returns the Kodi version. */
    public KodiVersion version(KodiEndpoint endpoint) {
        JsonNode result = call(endpoint, "Application.GetProperties",
                Map.of("properties", List.of("name", "version")));
        JsonNode version = result.path("version");
        String name = result.path("name").isString() ? result.path("name").asString() : DEFAULT_NAME;
        return new KodiVersion(name, version.path("major").asInt(0), version.path("minor").asInt(0));
    }

    /**
     * Looks up a movie in Kodi's library.
     *
     * @param file file as Kodi sees it, e.g. {@code smb://nas/filme/Heat/Heat.mkv}; for multi-part movies the
     *             first part
     * @return Kodi's ID of the movie, or empty if Kodi does not know it
     */
    public Optional<Integer> findMovie(KodiEndpoint endpoint, String file) {
        Map<String, Object> filter = Map.of("field", "path", "operator", "is", "value", KodiFiles.directoryOf(file));
        JsonNode movies = call(endpoint, "VideoLibrary.GetMovies",
                Map.of("filter", filter, "properties", List.of("file"))).path("movies");
        for (JsonNode movie : movies) {
            if (KodiFiles.matches(movie.path("file").asString(), file)) {
                return Optional.of(movie.path("movieid").asInt());
            }
        }
        return Optional.empty();
    }

    /**
     * Looks up a movie set in Kodi's library by its title, ignoring case.
     *
     * @return Kodi's ID of the set, or empty if Kodi knows none with that title
     */
    public Optional<Integer> findMovieSet(KodiEndpoint endpoint, String title) {
        JsonNode sets = call(endpoint, "VideoLibrary.GetMovieSets", Map.of("properties", List.of("title")))
                .path("sets");
        for (JsonNode set : sets) {
            if (set.path("title").asString("").equalsIgnoreCase(title)) {
                return Optional.of(set.path("setid").asInt());
            }
        }
        return Optional.empty();
    }

    /** @return Kodi's ID of the set the movie belongs to, or empty without a set */
    public Optional<Integer> movieSetOf(KodiEndpoint endpoint, int movieId) {
        int setId = call(endpoint, "VideoLibrary.GetMovieDetails",
                Map.of("movieid", movieId, "properties", List.of("setid"))).path("moviedetails").path("setid").asInt(0);
        return setId > 0 ? Optional.of(setId) : Optional.empty();
    }

    /**
     * Sets title, plot and images of a movie set.
     *
     * @param plot plot, or {@code null} to clear it
     * @param art  images per Kodi image type ({@code poster}, …) as paths as Kodi sees them; {@code null} removes an
     *             image, types not mentioned stay as they are
     */
    public void setMovieSetDetails(KodiEndpoint endpoint, int setId, String title, String plot,
            Map<String, String> art) {
        Map<String, Object> params = new HashMap<>();
        params.put("setid", setId);
        params.put("title", title);
        params.put("plot", plot == null ? "" : plot);
        if (!art.isEmpty()) {
            params.put("art", new HashMap<>(art));
        }
        call(endpoint, "VideoLibrary.SetMovieSetDetails", params);
    }

    /**
     * Removes images from Kodi's texture cache so Kodi reloads an image replaced under the same path.
     *
     * @param urls paths of the images as Kodi sees them
     */
    public void removeTextures(KodiEndpoint endpoint, Collection<String> urls) {
        if (urls.isEmpty()) {
            return;
        }
        List<Map<String, String>> rules = urls.stream()
                .map(url -> Map.of("field", "url", "operator", "is", "value", url))
                .toList();
        JsonNode textures = call(endpoint, "Textures.GetTextures",
                Map.of("properties", List.of("url"), "filter", Map.of("or", rules))).path("textures");
        for (JsonNode texture : textures) {
            call(endpoint, "Textures.RemoveTexture", Map.of("textureid", texture.path("textureid").asInt()));
        }
    }

    /** Kodi rereads the NFO and images of the movie. */
    public void refreshMovie(KodiEndpoint endpoint, int movieId) {
        call(endpoint, "VideoLibrary.RefreshMovie", Map.of("movieid", movieId, "ignorenfo", false));
    }

    /**
     * Looks up a TV show in Kodi's library by its folder.
     *
     * @param directory TV show folder as Kodi sees it, with a trailing separator
     * @return Kodi's ID of the TV show, or empty if Kodi does not know it
     */
    public Optional<Integer> findTvShow(KodiEndpoint endpoint, String directory) {
        Map<String, Object> filter = Map.of("field", "path", "operator", "is", "value", directory);
        JsonNode shows = call(endpoint, "VideoLibrary.GetTVShows",
                Map.of("filter", filter, "properties", List.of("file"))).path("tvshows");
        for (JsonNode show : shows) {
            if (show.path("file").asString().equalsIgnoreCase(directory)) {
                return Optional.of(show.path("tvshowid").asInt());
            }
        }
        return Optional.empty();
    }

    /**
     * Looks up an episode in Kodi's library. For multi-episode files Kodi knows several episodes with the same
     * file; season and number tell them apart.
     *
     * @param file file as Kodi sees it
     * @return Kodi's ID of the episode, or empty if Kodi does not know it
     */
    public Optional<Integer> findEpisode(KodiEndpoint endpoint, String file, int season, int episode) {
        Map<String, Object> filter = Map.of("field", "path", "operator", "is", "value", KodiFiles.directoryOf(file));
        JsonNode episodes = call(endpoint, "VideoLibrary.GetEpisodes",
                Map.of("filter", filter, "properties", List.of("file", "season", "episode"))).path("episodes");
        for (JsonNode candidate : episodes) {
            if (KodiFiles.matches(candidate.path("file").asString(), file)
                    && candidate.path("season").asInt(-1) == season && candidate.path("episode").asInt(-1) == episode) {
                return Optional.of(candidate.path("episodeid").asInt());
            }
        }
        return Optional.empty();
    }

    /** Kodi rereads {@code tvshow.nfo} and the images of the TV show, but not the episodes. */
    public void refreshTvShow(KodiEndpoint endpoint, int tvShowId) {
        call(endpoint, "VideoLibrary.RefreshTVShow",
                Map.of("tvshowid", tvShowId, "ignorenfo", false, "refreshepisodes", false));
    }

    /** Kodi rereads the NFO and thumbnail of the episode. */
    public void refreshEpisode(KodiEndpoint endpoint, int episodeId) {
        call(endpoint, "VideoLibrary.RefreshEpisode", Map.of("episodeid", episodeId, "ignorenfo", false));
    }

    /** @return the video sources of the device */
    public KodiSources videoSources(KodiEndpoint endpoint) {
        List<String> files = new ArrayList<>();
        for (JsonNode source : call(endpoint, "Files.GetSources", Map.of("media", "video")).path("sources")) {
            files.add(source.path("file").asString());
        }
        return KodiSources.of(files);
    }

    /** @return whether Kodi is currently scanning or cleaning its video library */
    public boolean isScanning(KodiEndpoint endpoint) {
        return call(endpoint, "XBMC.GetInfoBooleans", Map.of("booleans", List.of(IS_SCANNING_VIDEO)))
                .path(IS_SCANNING_VIDEO).asBoolean(false);
    }

    /**
     * Kodi scans the directory for new media. Kodi does not update known entries while doing so. If a scan is
     * already running, Kodi cancels it instead; so only call this through the {@link KodiLibraryScheduler}.
     *
     * @param directory directory as Kodi sees it, with a trailing separator
     */
    void scan(KodiEndpoint endpoint, String directory) {
        call(endpoint, "VideoLibrary.Scan", Map.of("directory", directory, "showdialogs", false));
    }

    /**
     * Kodi removes movies in or below the directory whose files no longer exist. During a scan Kodi discards the
     * call; so only call this through the {@link KodiLibraryScheduler}.
     *
     * @param directory directory as Kodi sees it, with a trailing separator
     */
    void cleanMovies(KodiEndpoint endpoint, String directory) {
        clean(endpoint, "movies", directory);
    }

    /**
     * Like {@link #cleanMovies}, for TV shows and episodes.
     *
     * @param directory directory as Kodi sees it, with a trailing separator
     */
    void cleanTvShows(KodiEndpoint endpoint, String directory) {
        clean(endpoint, "tvshows", directory);
    }

    private void clean(KodiEndpoint endpoint, String content, String directory) {
        call(endpoint, "VideoLibrary.Clean", Map.of("content", content, "directory", directory, "showdialogs", false));
    }

    /**
     * Returns the playback state of all movies in or below the directory.
     *
     * @param directory directory as Kodi sees it, with a trailing separator
     */
    public List<KodiPlayback> moviePlaybacks(KodiEndpoint endpoint, String directory) {
        return playbacks(endpoint, "VideoLibrary.GetMovies", "movies", directory, List.of("file", "playcount",
                "lastplayed", "userrating"));
    }

    /**
     * Returns the playback state of all episodes in or below the directory.
     *
     * @param directory directory as Kodi sees it, with a trailing separator
     */
    public List<KodiPlayback> episodePlaybacks(KodiEndpoint endpoint, String directory) {
        return playbacks(endpoint, "VideoLibrary.GetEpisodes", "episodes", directory, List.of("file", "season",
                "episode", "playcount", "lastplayed", "userrating"));
    }

    private List<KodiPlayback> playbacks(KodiEndpoint endpoint, String method, String resultKey, String directory,
                                         List<String> properties) {
        Map<String, Object> filter = Map.of("field", "path", "operator", "startswith", "value", directory);
        List<KodiPlayback> playbacks = new ArrayList<>();
        int total;
        do {
            int start = playbacks.size();
            JsonNode result = call(endpoint, method, Map.of("filter", filter, "properties", properties,
                    "limits", Map.of("start", start, "end", start + PAGE_SIZE)));
            JsonNode items = result.path(resultKey);
            items.forEach(item -> playbacks.add(playback(item)));
            total = result.path("limits").path("total").asInt(0);
            if (items.isEmpty()) {
                break;
            }
        } while (playbacks.size() < total);
        return playbacks;
    }

    private static KodiPlayback playback(JsonNode item) {
        return new KodiPlayback(item.path("file").asString(), optionalInt(item.path("season")),
                optionalInt(item.path("episode")), item.path("playcount").asInt(0),
                dateTime(item.path("lastplayed").asString("")),
                item.path("userrating").asInt(0));
    }

    private static Integer optionalInt(JsonNode node) {
        return node.isNumber() ? node.asInt() : null;
    }

    /** Kodi reports "never played" as empty text. */
    private static LocalDateTime dateTime(String value) {
        try {
            return value.isBlank() ? null : LocalDateTime.parse(value, KODI_DATE_TIME);
        } catch (DateTimeParseException e) {
            log.debug("Ignoring unparsable Kodi date '{}'", value);
            return null;
        }
    }

    private JsonNode call(KodiEndpoint endpoint, String method, Map<String, Object> params) {
        log.debug("Calling {} on {}", method, endpoint);
        HttpResponse<String> response = send(endpoint, request(endpoint, method, params));
        if (response.statusCode() == UNAUTHORIZED) {
            throw new KodiException(LocalizedText.of("kodiRpc.unauthorized").with("address", endpoint.address()));
        }
        if (response.statusCode() != OK) {
            throw new KodiException(LocalizedText.of("kodiRpc.httpStatus")
                    .with("address", endpoint.address())
                    .with("status", response.statusCode()));
        }
        JsonNode body = parse(endpoint, response.body());
        JsonNode error = body.path("error");
        if (!error.isMissingNode() && !error.isNull()) {
            throw new KodiException(LocalizedText.of("kodiRpc.error")
                    .with("method", method)
                    .with("message", error.path("message").asString())
                    .with("code", error.path("code").asInt(0)));
        }
        return body.path("result");
    }

    private HttpRequest request(KodiEndpoint endpoint, String method, Map<String, Object> params) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jsonrpc", JSON_RPC_VERSION);
        payload.put("method", method);
        payload.put("params", params);
        payload.put("id", requestIds.incrementAndGet());
        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint.uri())
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)));
        if (endpoint.hasCredentials()) {
            builder.header("Authorization", basicAuthorization(endpoint));
        }
        return builder.build();
    }

    private static String basicAuthorization(KodiEndpoint endpoint) {
        String password = endpoint.password() == null ? "" : endpoint.password();
        String credentials = endpoint.username() + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> send(KodiEndpoint endpoint, HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpConnectTimeoutException e) {
            throw unreachable(endpoint, e);
        } catch (HttpTimeoutException e) {
            throw new KodiException(LocalizedText.of("kodiRpc.timeout").with("address", endpoint.address()), e);
        } catch (IOException e) {
            throw unreachable(endpoint, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KodiException(LocalizedText.of("kodiRpc.interrupted"), e);
        }
    }

    private static KodiException unreachable(KodiEndpoint endpoint, IOException cause) {
        return new KodiException(LocalizedText.of("kodiRpc.unreachable")
                .with("address", endpoint.address())
                .with("port", String.valueOf(endpoint.port())), cause);
    }

    private JsonNode parse(KodiEndpoint endpoint, String body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            if (node == null || !node.isObject()) {
                throw invalidResponse(endpoint, null);
            }
            return node;
        } catch (JacksonException e) {
            throw invalidResponse(endpoint, e);
        }
    }

    private static KodiException invalidResponse(KodiEndpoint endpoint, Throwable cause) {
        return new KodiException(LocalizedText.of("kodiRpc.invalidResponse").with("address", endpoint.address()),
                cause);
    }
}
