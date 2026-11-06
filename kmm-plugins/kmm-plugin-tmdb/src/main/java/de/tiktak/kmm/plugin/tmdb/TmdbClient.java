package de.tiktak.kmm.plugin.tmdb;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import de.tiktak.kmm.plugin.api.ScraperException;
import de.tiktak.kmm.plugin.api.ScraperTexts;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Minimal HTTP client for the TMDB API v3. Turns errors into understandable {@link ScraperException}s for the user
 * interface.
 */
@RequiredArgsConstructor
class TmdbClient {

    static final String API_KEY_PARAMETER = "api_key";
    private static final int V3_API_KEY_MAX_LENGTH = 40;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final int OK = 200;
    private static final int UNAUTHORIZED = 401;
    private static final int NOT_FOUND = 404;
    private static final int TOO_MANY_REQUESTS = 429;
    private static final String BEARER_PREFIX = "Bearer ";

    private final HttpClient httpClient;
    private final TmdbSettings settings;
    private final String userAgent;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    /**
     * @return the response, or empty if TMDB does not know the entry (HTTP 404)
     * @throws ScraperException on a missing or invalid API key, the request limit, server or network errors
     */
    Optional<JsonNode> get(String path, Map<String, String> parameters) {
        HttpResponse<String> response = send(request(path, parameters));
        return switch (response.statusCode()) {
            case OK -> Optional.of(parse(response.body()));
            case NOT_FOUND -> Optional.empty();
            case UNAUTHORIZED -> throw new ScraperException(ScraperTexts.apiKeyRejected(AbstractTmdbScraper.PROVIDER));
            case TOO_MANY_REQUESTS -> throw new ScraperException(
                    ScraperTexts.rateLimitReached(AbstractTmdbScraper.PROVIDER));
            default -> throw new ScraperException(
                    ScraperTexts.httpError(AbstractTmdbScraper.PROVIDER, response.statusCode()));
        };
    }

    private HttpRequest request(String path, Map<String, String> parameters) {
        Map<String, String> query = new LinkedHashMap<>(parameters);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("User-Agent", userAgent)
                .GET();
        if (usesBearerToken()) {
            builder.header("Authorization", BEARER_PREFIX + settings.apiKey());
        } else {
            query.put(API_KEY_PARAMETER, settings.apiKey());
        }
        return builder.uri(uri(path, query)).build();
    }

    /** TMDB v4 read access tokens are long JWTs, v3 API keys are 32 characters long. */
    private boolean usesBearerToken() {
        return settings.apiKey().length() > V3_API_KEY_MAX_LENGTH;
    }

    private URI uri(String path, Map<String, String> query) {
        String queryString = query.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
        return URI.create(settings.baseUrl().toString().replaceAll("/+$", "") + path + "?" + queryString);
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ScraperException(ScraperTexts.unreachable(AbstractTmdbScraper.PROVIDER, e.getMessage()), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ScraperException(ScraperTexts.interrupted(AbstractTmdbScraper.PROVIDER), e);
        }
    }

    private JsonNode parse(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (JacksonException e) {
            throw new ScraperException(ScraperTexts.unexpectedResponse(AbstractTmdbScraper.PROVIDER), e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
