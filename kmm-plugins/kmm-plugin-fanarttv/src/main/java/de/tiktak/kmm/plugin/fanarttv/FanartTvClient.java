package de.tiktak.kmm.plugin.fanarttv;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Pattern;

import de.tiktak.kmm.plugin.api.ScraperException;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * HTTP-Client für die fanart.tv-API v3. Übersetzt Fehler in verständliche {@link ScraperException}s.
 */
@RequiredArgsConstructor
class FanartTvClient {

    /** fanart.tv akzeptiert für Filme TMDB- und IMDb-IDs; sie landen im URL-Pfad. */
    private static final Pattern MOVIE_ID = Pattern.compile("[0-9]{1,10}|tt[0-9]{7,10}");
    private static final Pattern TVDB_ID = Pattern.compile("[0-9]{1,10}");
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final int OK = 200;
    private static final int UNAUTHORIZED = 401;
    private static final int FORBIDDEN = 403;
    private static final int NOT_FOUND = 404;

    private final HttpClient httpClient;
    private final FanartTvSettings settings;
    private final String userAgent;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    /**
     * @return die Bilder des Films oder leer, wenn fanart.tv ihn nicht kennt
     */
    Optional<JsonNode> movie(String movieId) {
        if (!MOVIE_ID.matcher(movieId).matches()) {
            return Optional.empty();
        }
        return get("/movies/" + movieId);
    }

    /**
     * @return die Bilder der Serie oder leer, wenn fanart.tv sie nicht kennt
     */
    Optional<JsonNode> tvShow(String tvdbId) {
        if (!TVDB_ID.matcher(tvdbId).matches()) {
            return Optional.empty();
        }
        return get("/tv/" + tvdbId);
    }

    private Optional<JsonNode> get(String path) {
        HttpResponse<String> response = send(request(path));
        return switch (response.statusCode()) {
            case OK -> Optional.of(parse(response.body()));
            case NOT_FOUND -> Optional.empty();
            case UNAUTHORIZED, FORBIDDEN -> throw new ScraperException(
                    "fanart.tv lehnt den API-Key ab. Bitte unter Einstellungen → API-Keys prüfen.");
            default -> throw new ScraperException("fanart.tv antwortet mit Fehler %d.".formatted(response.statusCode()));
        };
    }

    private HttpRequest request(String path) {
        StringBuilder query = new StringBuilder("api_key=").append(encode(settings.apiKey()));
        if (settings.hasClientKey()) {
            query.append("&client_key=").append(encode(settings.clientKey()));
        }
        URI uri = URI.create(settings.baseUrl().toString().replaceAll("/+$", "") + path + "?" + query);
        return HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("User-Agent", userAgent)
                .GET()
                .build();
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ScraperException("fanart.tv ist nicht erreichbar: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ScraperException("Die Anfrage an fanart.tv wurde unterbrochen.", e);
        }
    }

    private JsonNode parse(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (JacksonException e) {
            throw new ScraperException("fanart.tv hat eine unerwartete Antwort geliefert.", e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
