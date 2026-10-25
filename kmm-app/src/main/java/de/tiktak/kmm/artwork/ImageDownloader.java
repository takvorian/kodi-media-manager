package de.tiktak.kmm.artwork;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Lädt ein Bild aus dem Internet. Erlaubt sind nur http(s)-Adressen, JPEG und PNG sowie eine
 * begrenzte Dateigröße, damit keine beliebigen Inhalte im Filmordner landen.
 */
@Component
@RequiredArgsConstructor
class ImageDownloader {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/jpg", "jpg",
            "image/png", "png");
    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    private static final int OK = 200;
    private static final String UNKNOWN_CONTENT_TYPE = "unbekannt";

    private final HttpClient httpClient;
    private final ArtworkProperties properties;

    DownloadedImage download(URI url) {
        if (url.getScheme() == null || !ALLOWED_SCHEMES.contains(url.getScheme().toLowerCase(Locale.ROOT))) {
            throw ImageDownloadException.invalidUrl(url.toString());
        }
        HttpResponse<InputStream> response = send(url);
        try (InputStream body = response.body()) {
            if (response.statusCode() != OK) {
                throw ImageDownloadException.downloadFailed(url, "HTTP " + response.statusCode(), null);
            }
            String contentType = contentType(response);
            String extension = Optional.ofNullable(EXTENSIONS.get(contentType))
                    .orElseThrow(() -> ImageDownloadException.notAnImage(url, contentType));
            return new DownloadedImage(readLimited(body, url), extension);
        } catch (IOException e) {
            throw ImageDownloadException.downloadFailed(url, e.getMessage(), e);
        }
    }

    private HttpResponse<InputStream> send(URI url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(url).timeout(TIMEOUT).GET().build();
            return httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException e) {
            throw ImageDownloadException.downloadFailed(url, e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ImageDownloadException.downloadFailed(url, "Download unterbrochen", e);
        }
    }

    private byte[] readLimited(InputStream body, URI url) throws IOException {
        long maxBytes = properties.maxDownloadSize().toBytes();
        byte[] content = body.readNBytes(Math.toIntExact(maxBytes + 1));
        if (content.length > maxBytes) {
            throw ImageDownloadException.tooLarge(url, maxBytes);
        }
        return content;
    }

    private static String contentType(HttpResponse<?> response) {
        return response.headers().firstValue("Content-Type")
                .map(value -> value.split(";")[0].strip().toLowerCase(Locale.ROOT))
                .orElse(UNKNOWN_CONTENT_TYPE);
    }
}
