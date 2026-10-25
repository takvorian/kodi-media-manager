package de.tiktak.kmm.kodi.rpc;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
     * Kodi durchsucht das Verzeichnis nach neuen Medien. Bekannte Einträge aktualisiert Kodi dabei nicht.
     *
     * @param directory Verzeichnis aus Sicht von Kodi mit abschließendem Trennzeichen
     */
    public void scan(KodiEndpoint endpoint, String directory) {
        call(endpoint, "VideoLibrary.Scan", Map.of("directory", directory, "showdialogs", false));
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
        } catch (HttpTimeoutException e) {
            throw new KodiException("Kodi unter %s antwortet nicht rechtzeitig.".formatted(endpoint.address()), e);
        } catch (IOException e) {
            throw new KodiException(("Kodi unter %s:%d ist nicht erreichbar. Läuft Kodi, und ist "
                    + "„Steuerung über HTTP erlauben“ aktiviert?").formatted(endpoint.address(), endpoint.port()), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KodiException("Die Anfrage an Kodi wurde unterbrochen.", e);
        }
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
