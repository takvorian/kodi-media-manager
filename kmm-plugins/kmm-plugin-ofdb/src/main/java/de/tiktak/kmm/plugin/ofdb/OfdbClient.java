package de.tiktak.kmm.plugin.ofdb;

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
 * Ruft Sitemaps und Filmseiten von OFDb ab, mit Abstand zwischen den Abrufen. Die Suche ist laut {@code robots.txt} für
 * Programme gesperrt und wird nicht benutzt.
 */
class OfdbClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private static final int OK = 200;
    private static final int NOT_FOUND = 404;
    /** Der Adressname ist OFDb egal; es zählt die ID. */
    private static final String ANY_NAME = "x";

    private final HttpClient httpClient;
    private final OfdbSettings settings;
    private final String userAgent;
    private final RequestThrottle throttle;

    OfdbClient(HttpClient httpClient, OfdbSettings settings, String userAgent) {
        this.httpClient = httpClient;
        this.settings = settings;
        this.userAgent = userAgent;
        this.throttle = new RequestThrottle(settings.requestDelay());
    }

    /** @param path Pfad der Sitemap, z. B. {@code /sitemap/sitemap1.xml} */
    String sitemap(String path) {
        return page(path).orElseThrow(() -> new ScraperException("OFDb: Die Sitemap %s fehlt.".formatted(path)));
    }

    Optional<String> film(int filmId) {
        return page("/film/%d,%s/".formatted(filmId, ANY_NAME));
    }

    Optional<String> plot(int filmId, int plotId) {
        return page("/film/%d,%d,%s/plot/".formatted(filmId, plotId, ANY_NAME));
    }

    private Optional<String> page(String path) {
        throttle.await();
        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(settings.baseUrl() + path))
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", userAgent)
                .GET()
                .build());
        return switch (response.statusCode()) {
            case OK -> Optional.of(response.body());
            case NOT_FOUND -> Optional.empty();
            default -> throw new ScraperException("OFDb antwortet mit Fehler %d.".formatted(response.statusCode()));
        };
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ScraperException("OFDb ist nicht erreichbar: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ScraperException("Die Anfrage an OFDb wurde unterbrochen.", e);
        }
    }
}
