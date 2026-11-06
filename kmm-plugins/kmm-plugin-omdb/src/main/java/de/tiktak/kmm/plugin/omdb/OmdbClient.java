package de.tiktak.kmm.plugin.omdb;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
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
 * HTTP client for the OMDb API. OMDb mostly reports errors with status 200 and {@code "Response": "False"}; they
 * become understandable {@link ScraperException}s, an unknown entry becomes an empty response.
 */
@RequiredArgsConstructor
class OmdbClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final int OK = 200;
    private static final int UNAUTHORIZED = 401;
    private static final String TRUE = "True";
    private static final String INVALID_KEY = "Invalid API key!";
    private static final String LIMIT_REACHED = "Request limit reached!";

    private final HttpClient httpClient;
    private final OmdbSettings settings;
    private final String userAgent;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    /**
     * @param imdbId checked IMDb ID
     * @return the details, or empty if OMDb does not know the entry
     */
    Optional<JsonNode> byImdbId(String imdbId) {
        URI uri = URI.create("%s/?i=%s&plot=full&apikey=%s".formatted(settings.baseUrl(), imdbId,
                URLEncoder.encode(settings.apiKey(), StandardCharsets.UTF_8)));
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("User-Agent", userAgent)
                .GET()
                .build());
        JsonNode body = response.statusCode() == OK || response.statusCode() == UNAUTHORIZED
                ? parse(response.body()) : null;
        if (body == null) {
            throw new ScraperException(ScraperTexts.httpError(OmdbEnrichmentScraper.PROVIDER, response.statusCode()));
        }
        if (TRUE.equals(body.path("Response").asString(""))) {
            return Optional.of(body);
        }
        String error = body.path("Error").asString("");
        return switch (error) {
            case INVALID_KEY -> throw new ScraperException(ScraperTexts.apiKeyRejected(OmdbEnrichmentScraper.PROVIDER));
            case LIMIT_REACHED -> throw new ScraperException(LocalizedText.of("scraper.omdb.dailyLimitReached"));
            default -> Optional.empty();
        };
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ScraperException(ScraperTexts.unreachable(OmdbEnrichmentScraper.PROVIDER, e.getMessage()), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ScraperException(ScraperTexts.interrupted(OmdbEnrichmentScraper.PROVIDER), e);
        }
    }

    private JsonNode parse(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (JacksonException e) {
            throw new ScraperException(ScraperTexts.unexpectedResponse(OmdbEnrichmentScraper.PROVIDER), e);
        }
    }
}
