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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Spricht die JSON-RPC-Schnittstelle von Kodi (ab Kodi 19) über HTTP an. Fehler werden in
 * verständliche {@link KodiException}s übersetzt.
 */
@Slf4j
public class KodiClient {

    private static final String JSON_RPC_VERSION = "2.0";
    private static final int OK = 200;
    private static final int UNAUTHORIZED = 401;
    private static final String DEFAULT_NAME = "Kodi";
    /** Einträge je Anfrage beim Lesen ganzer Quellen; hält Antworten über langsame Verbindungen klein. */
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

    /** Prüft die Verbindung und liefert die Kodi-Version. */
    public KodiVersion version(KodiEndpoint endpoint) {
        JsonNode result = call(endpoint, "Application.GetProperties",
                Map.of("properties", List.of("name", "version")));
        JsonNode version = result.path("version");
        String name = result.path("name").isString() ? result.path("name").asString() : DEFAULT_NAME;
        return new KodiVersion(name, version.path("major").asInt(0), version.path("minor").asInt(0));
    }

    /**
     * Sucht einen Film in Kodis Bibliothek.
     *
     * @param file Datei aus Sicht von Kodi, z. B. {@code smb://nas/filme/Heat/Heat.mkv}; bei
     *             mehrteiligen Filmen der erste Teil
     * @return Kodis ID des Films oder leer, wenn Kodi ihn nicht kennt
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

    /** Kodi liest NFO und Bilder des Films neu ein. */
    public void refreshMovie(KodiEndpoint endpoint, int movieId) {
        call(endpoint, "VideoLibrary.RefreshMovie", Map.of("movieid", movieId, "ignorenfo", false));
    }

    /**
     * Sucht eine Serie in Kodis Bibliothek über ihren Ordner.
     *
     * @param directory Serienordner aus Sicht von Kodi mit abschließendem Trennzeichen
     * @return Kodis ID der Serie oder leer, wenn Kodi sie nicht kennt
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
     * Sucht eine Episode in Kodis Bibliothek. Bei Mehrfachepisoden kennt Kodi mehrere Episoden mit
     * derselben Datei; Staffel und Nummer unterscheiden sie.
     *
     * @param file Datei aus Sicht von Kodi
     * @return Kodis ID der Episode oder leer, wenn Kodi sie nicht kennt
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

    /** Kodi liest {@code tvshow.nfo} und die Bilder der Serie neu ein, die Episoden nicht. */
    public void refreshTvShow(KodiEndpoint endpoint, int tvShowId) {
        call(endpoint, "VideoLibrary.RefreshTVShow",
                Map.of("tvshowid", tvShowId, "ignorenfo", false, "refreshepisodes", false));
    }

    /** Kodi liest NFO und Standbild der Episode neu ein. */
    public void refreshEpisode(KodiEndpoint endpoint, int episodeId) {
        call(endpoint, "VideoLibrary.RefreshEpisode", Map.of("episodeid", episodeId, "ignorenfo", false));
    }

    /**
     * Kodi durchsucht das Verzeichnis nach neuen Medien. Bekannte Einträge aktualisiert Kodi dabei nicht.
     *
     * @param directory Verzeichnis aus Sicht von Kodi mit abschließendem Trennzeichen
     */
    public void scan(KodiEndpoint endpoint, String directory) {
        call(endpoint, "VideoLibrary.Scan", Map.of("directory", directory, "showdialogs", false));
    }

    /**
     * Kodi entfernt Filme in oder unterhalb des Verzeichnisses, deren Dateien es nicht mehr gibt. Kodi arbeitet
     * Scan und Bereinigen nacheinander ab, ein vorher gestarteter Scan ist also fertig.
     *
     * @param directory Verzeichnis aus Sicht von Kodi mit abschließendem Trennzeichen
     */
    public void cleanMovies(KodiEndpoint endpoint, String directory) {
        call(endpoint, "VideoLibrary.Clean", Map.of("content", "movies", "directory", directory, "showdialogs", false));
    }

    /**
     * Liefert den Wiedergabestand aller Filme in oder unterhalb des Verzeichnisses.
     *
     * @param directory Verzeichnis aus Sicht von Kodi mit abschließendem Trennzeichen
     */
    public List<KodiPlayback> moviePlaybacks(KodiEndpoint endpoint, String directory) {
        return playbacks(endpoint, "VideoLibrary.GetMovies", "movies", directory, List.of("file", "playcount",
                "lastplayed", "userrating"));
    }

    /**
     * Liefert den Wiedergabestand aller Episoden in oder unterhalb des Verzeichnisses.
     *
     * @param directory Verzeichnis aus Sicht von Kodi mit abschließendem Trennzeichen
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

    /** Kodi meldet „nie gespielt“ als leeren Text. */
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
            throw new KodiException("Kodi unter %s lehnt Benutzername oder Passwort ab.".formatted(endpoint.address()));
        }
        if (response.statusCode() != OK) {
            throw new KodiException("Kodi unter %s antwortet mit HTTP-Status %d."
                    .formatted(endpoint.address(), response.statusCode()));
        }
        JsonNode body = parse(endpoint, response.body());
        JsonNode error = body.path("error");
        if (!error.isMissingNode() && !error.isNull()) {
            throw new KodiException("Kodi meldet bei %s einen Fehler: %s (%d)."
                    .formatted(method, error.path("message").asString(), error.path("code").asInt(0)));
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
            throw new KodiException("Kodi unter %s antwortet nicht rechtzeitig.".formatted(endpoint.address()), e);
        } catch (IOException e) {
            throw unreachable(endpoint, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KodiException("Die Anfrage an Kodi wurde unterbrochen.", e);
        }
    }

    private static KodiException unreachable(KodiEndpoint endpoint, IOException cause) {
        return new KodiException(("Kodi unter %s:%d ist nicht erreichbar. Läuft Kodi, und ist "
                + "„Steuerung über HTTP erlauben“ aktiviert?").formatted(endpoint.address(), endpoint.port()), cause);
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
        return new KodiException("Die Antwort von %s ist keine gültige Kodi-Antwort.".formatted(endpoint.address()),
                cause);
    }
}
