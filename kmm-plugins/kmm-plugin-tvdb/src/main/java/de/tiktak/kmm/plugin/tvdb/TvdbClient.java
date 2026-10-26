package de.tiktak.kmm.plugin.tvdb;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import de.tiktak.kmm.plugin.api.ScraperException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Minimaler HTTP-Client für die TVDB-API v4: meldet sich mit API-Key und PIN an und übersetzt Fehler in
 * verständliche {@link ScraperException}s für die Oberfläche.
 */
class TvdbClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final int OK = 200;
    private static final int UNAUTHORIZED = 401;
    private static final int NOT_FOUND = 404;
    private static final int TOO_MANY_REQUESTS = 429;
    private static final String REJECTED_KEY =
            "TVDB lehnt den API-Key oder die PIN ab. Bitte unter Einstellungen → API-Keys prüfen.";

    private final HttpClient httpClient;
    private final TvdbSettings settings;
    private final String userAgent;
    private final Clock clock;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    TvdbClient(HttpClient httpClient, TvdbSettings settings, String userAgent, Clock clock) {
        this.httpClient = httpClient;
        this.settings = settings;
        this.userAgent = userAgent;
        this.clock = clock;
    }

    /**
     * @return die Antwort (Inhalt unter {@code data}) oder leer, wenn TVDB den Eintrag nicht kennt (HTTP 404)
     * @throws ScraperException bei ungültigem Key, Anfragelimit, Server- oder Netzwerkfehlern
     */
    Optional<JsonNode> get(String path, Map<String, String> parameters) {
        HttpResponse<String> response = send(request(path, parameters, token()));
        if (response.statusCode() == UNAUTHORIZED) {
            // Ein abgelaufenes oder widerrufenes Token: einmal neu anmelden.
            TvdbTokens.forget(tokenKey());
            response = send(request(path, parameters, token()));
        }
        return switch (response.statusCode()) {
            case OK -> Optional.of(parse(response.body()));
            case NOT_FOUND -> Optional.empty();
            case UNAUTHORIZED -> throw new ScraperException(REJECTED_KEY);
            case TOO_MANY_REQUESTS -> throw new ScraperException(
                    "Das Anfragelimit von TVDB ist erreicht. Bitte später erneut versuchen.");
            default -> throw new ScraperException("TVDB antwortet mit Fehler %d.".formatted(response.statusCode()));
        };
    }

    private String token() {
        return TvdbTokens.valid(tokenKey(), clock).orElseGet(this::login);
    }

    private String login() {
        Map<String, String> credentials = new LinkedHashMap<>();
        credentials.put("apikey", settings.apiKey());
        if (!settings.pin().isEmpty()) {
            credentials.put("pin", settings.pin());
        }
        HttpRequest request = builder(uri("/login", Map.of()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(credentials)))
                .build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() == UNAUTHORIZED) {
            throw new ScraperException(REJECTED_KEY);
        }
        if (response.statusCode() != OK) {
            throw new ScraperException("Die Anmeldung bei TVDB ist fehlgeschlagen (Fehler %d)."
                    .formatted(response.statusCode()));
        }
        String token = JsonNodes.text(parse(response.body()).path("data"), "token")
                .orElseThrow(() -> new ScraperException("TVDB hat bei der Anmeldung kein Token geliefert."));
        TvdbTokens.remember(tokenKey(), token, clock);
        return token;
    }

    /** Je Key, PIN und Server ein Token. */
    private String tokenKey() {
        return settings.baseUrl() + "|" + settings.apiKey() + "|" + settings.pin();
    }

    private HttpRequest request(String path, Map<String, String> parameters, String token) {
        return builder(uri(path, parameters)).header("Authorization", "Bearer " + token).GET().build();
    }

    private HttpRequest.Builder builder(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("User-Agent", userAgent);
    }

    private URI uri(String path, Map<String, String> parameters) {
        String query = parameters.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
        String base = settings.baseUrl().toString().replaceAll("/+$", "") + path;
        return URI.create(query.isEmpty() ? base : base + "?" + query);
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ScraperException("TVDB ist nicht erreichbar: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ScraperException("Die Anfrage an TVDB wurde unterbrochen.", e);
        }
    }

    private JsonNode parse(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (JacksonException e) {
            throw new ScraperException("TVDB hat eine unerwartete Antwort geliefert.", e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
