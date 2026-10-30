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

/**
 * Ruft Filmseiten von Moviepilot ab, mit Abstand zwischen den Abrufen. Nur Filmseiten: Die Suche ist laut
 * {@code robots.txt} für Programme gesperrt.
 */
class MoviepilotClient {

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
     * @param slug Adressname des Films, z. B. {@code der-pate}
     * @return die Seite oder leer, wenn es sie nicht gibt
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
            default -> throw new ScraperException("Moviepilot antwortet mit Fehler %d."
                    .formatted(response.statusCode()));
        };
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ScraperException("Moviepilot ist nicht erreichbar: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ScraperException("Die Anfrage an Moviepilot wurde unterbrochen.", e);
        }
    }
}
