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

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.plugin.api.ScraperException;
import de.tiktak.kmm.plugin.api.ScraperTexts;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Minimal HTTP client for the TVDB API v4: logs in with API key and PIN and turns errors into understandable
 * {@link ScraperException}s for the user interface.
 */
class TvdbClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final int OK = 200;
    private static final int UNAUTHORIZED = 401;
    private static final int NOT_FOUND = 404;
    private static final int TOO_MANY_REQUESTS = 429;
    private static final String PROVIDER = AbstractTvdbScraper.PROVIDER;
    private static final LocalizedText REJECTED_KEY = LocalizedText.of("scraper.tvdb.credentialsRejected");

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
     * @return the response (content under {@code data}), or empty if TVDB does not know the entry (HTTP 404)
     * @throws ScraperException on an invalid key, the request limit, server or network errors
     */
    Optional<JsonNode> get(String path, Map<String, String> parameters) {
        HttpResponse<String> response = send(request(path, parameters, token()));
        if (response.statusCode() == UNAUTHORIZED) {
            // An expired or revoked token: log in again once.
            TvdbTokens.forget(tokenKey());
            response = send(request(path, parameters, token()));
        }
        return switch (response.statusCode()) {
            case OK -> Optional.of(parse(response.body()));
            case NOT_FOUND -> Optional.empty();
            case UNAUTHORIZED -> throw new ScraperException(REJECTED_KEY);
            case TOO_MANY_REQUESTS -> throw new ScraperException(ScraperTexts.rateLimitReached(PROVIDER));
            default -> throw new ScraperException(ScraperTexts.httpError(PROVIDER, response.statusCode()));
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
            throw new ScraperException(LocalizedText.of("scraper.tvdb.loginFailed")
                    .with("status", response.statusCode()));
        }
        String token = JsonNodes.text(parse(response.body()).path("data"), "token")
                .orElseThrow(() -> new ScraperException(LocalizedText.of("scraper.tvdb.noToken")));
        TvdbTokens.remember(tokenKey(), token, clock);
        return token;
    }

    /** One token per key, PIN and server. */
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
            throw new ScraperException(ScraperTexts.unreachable(PROVIDER, e.getMessage()), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ScraperException(ScraperTexts.interrupted(PROVIDER), e);
        }
    }

    private JsonNode parse(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (JacksonException e) {
            throw new ScraperException(ScraperTexts.unexpectedResponse(PROVIDER), e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
