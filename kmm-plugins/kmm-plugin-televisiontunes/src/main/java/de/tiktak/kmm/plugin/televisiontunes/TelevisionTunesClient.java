package de.tiktak.kmm.plugin.televisiontunes;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.plugin.api.ScraperException;
import de.tiktak.kmm.plugin.api.ScraperTexts;
import lombok.RequiredArgsConstructor;

/**
 * HTTP access to TelevisionTunes: search, show pages and MP3 files. Errors become understandable
 * {@link ScraperException}s.
 */
@RequiredArgsConstructor
class TelevisionTunesClient {

    /** Name of the provider in messages for the user. */
    static final String PROVIDER = "TelevisionTunes";
    /** Theme music is rarely longer than a few minutes; larger files are none. */
    static final long MAX_AUDIO_BYTES = 30L * 1024 * 1024;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final int OK = 200;
    private static final int NOT_FOUND = 404;
    private static final String AUDIO_CONTENT_TYPE = "audio/";

    private final HttpClient httpClient;
    private final TelevisionTunesSettings settings;
    private final String userAgent;

    String search(String query) {
        return page("/search.php?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8));
    }

    /** @param path path of the show page, e.g. {@code /show/ALF} */
    String show(String path) {
        return page(path);
    }

    /**
     * Downloads an MP3 file to {@code target}.
     *
     * @throws ScraperException if the response is no audio file or too large
     */
    void download(URI audio, Path target) {
        HttpResponse<InputStream> response = send(request(audio), HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            if (response.statusCode() != OK || !contentType.startsWith(AUDIO_CONTENT_TYPE)) {
                throw new ScraperException(LocalizedText.of("scraper.televisiontunes.noAudioFile")
                        .with("url", audio).with("status", response.statusCode()).with("contentType", contentType));
            }
            copyLimited(body, target);
        } catch (IOException e) {
            throw new ScraperException(LocalizedText.of("scraper.televisiontunes.downloadFailed")
                    .with("reason", e.getMessage()), e);
        }
    }

    private String page(String path) {
        HttpResponse<String> response = send(request(settings.resolve(path)),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return switch (response.statusCode()) {
            case OK -> response.body();
            case NOT_FOUND -> "";
            default -> throw new ScraperException(ScraperTexts.httpError(PROVIDER, response.statusCode()));
        };
    }

    private HttpRequest request(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", userAgent)
                .GET()
                .build();
    }

    private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
        try {
            return httpClient.send(request, handler);
        } catch (IOException e) {
            throw new ScraperException(ScraperTexts.unreachable(PROVIDER, e.getMessage()), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ScraperException(ScraperTexts.interrupted(PROVIDER), e);
        }
    }

    private static void copyLimited(InputStream body, Path target) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        long total = 0;
        try (OutputStream output = Files.newOutputStream(target)) {
            for (int read = body.read(buffer); read >= 0; read = body.read(buffer)) {
                total += read;
                if (total > MAX_AUDIO_BYTES) {
                    throw new IOException("File larger than %d MB".formatted(MAX_AUDIO_BYTES / 1024 / 1024));
                }
                output.write(buffer, 0, read);
            }
        } catch (IOException e) {
            Files.deleteIfExists(target);
            throw e;
        }
    }
}
