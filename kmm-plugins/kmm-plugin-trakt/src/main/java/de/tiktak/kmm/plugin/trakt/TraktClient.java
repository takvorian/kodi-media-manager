package de.tiktak.kmm.plugin.trakt;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.plugin.api.ScraperException;
import de.tiktak.kmm.plugin.api.ScraperTexts;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * HTTP client for the Trakt API v2 with a client ID, without login. Turns errors into understandable
 * {@link ScraperException}s.
 */
@RequiredArgsConstructor
class TraktClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final String API_VERSION = "2";
    private static final int OK = 200;
    private static final int UNAUTHORIZED = 401;
    private static final int FORBIDDEN = 403;
    private static final int NOT_FOUND = 404;

    private final HttpClient httpClient;
    private final TraktSettings settings;
    private final String userAgent;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    /**
     * @param path path including the query, e.g. {@code /movies/tt0113277/ratings}; its parts are checked
     * @return the response, or empty if Trakt does not know the entry
     */
    Optional<JsonNode> get(String path) {
        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(settings.baseUrl() + path))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("User-Agent", userAgent)
                .header("trakt-api-version", API_VERSION)
                .header("trakt-api-key", settings.clientId())
                .GET()
                .build());
        return switch (response.statusCode()) {
            case OK -> Optional.of(parse(response.body()));
            case NOT_FOUND -> Optional.empty();
            case UNAUTHORIZED, FORBIDDEN -> throw new ScraperException(
                    LocalizedText.of("scraper.trakt.clientIdRejected"));
            default -> throw new ScraperException(
                    ScraperTexts.httpError(TraktEnrichmentScraper.PROVIDER, response.statusCode()));
        };
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ScraperException(ScraperTexts.unreachable(TraktEnrichmentScraper.PROVIDER, e.getMessage()), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ScraperException(ScraperTexts.interrupted(TraktEnrichmentScraper.PROVIDER), e);
        }
    }

    private JsonNode parse(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (JacksonException e) {
            throw new ScraperException(ScraperTexts.unexpectedResponse(TraktEnrichmentScraper.PROVIDER), e);
        }
    }
}
