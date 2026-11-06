package de.tiktak.kmm.plugin.moviepilot;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

import de.tiktak.kmm.plugin.api.RequestThrottle;
import de.tiktak.kmm.plugin.api.ScraperException;
import de.tiktak.kmm.plugin.api.ScraperTexts;

/**
 * Fetches movie pages from Moviepilot, with a gap between the requests. Only movie pages: according to
 * {@code robots.txt} the search is closed to programs.
 */
class MoviepilotClient {

    /** Name of the provider in messages for the user. */
    static final String PROVIDER = "Moviepilot";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final int OK = 200;
    private static final int NOT_FOUND = 404;
    private static final int GONE = 410;

    private final HttpClient httpClient;
    private final MoviepilotSettings settings;
    private final String userAgent;
    private final RequestThrottle throttle;

    MoviepilotClient(HttpClient httpClient, MoviepilotSettings settings, String userAgent) {
        this.httpClient = httpClient;
        this.settings = settings;
        this.userAgent = userAgent;
        this.throttle = new RequestThrottle(settings.requestDelay());
    }

    /**
     * @param slug address name of the movie, e.g. {@code der-pate}
     * @return the page, or empty if it does not exist
     */
    Optional<String> movie(String slug) {
        throttle.await();
        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(settings.baseUrl() + "/movies/" + slug))
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", userAgent)
                .GET()
                .build());
        return switch (response.statusCode()) {
            case OK -> Optional.of(response.body());
            case NOT_FOUND, GONE -> Optional.empty();
            default -> throw new ScraperException(ScraperTexts.httpError(PROVIDER, response.statusCode()));
        };
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
}
