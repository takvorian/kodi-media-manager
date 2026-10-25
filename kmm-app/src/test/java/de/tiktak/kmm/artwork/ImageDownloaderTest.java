package de.tiktak.kmm.artwork;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.util.unit.DataSize;

import com.sun.net.httpserver.HttpServer;

class ImageDownloaderTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3};

    private HttpServer server;
    private ImageDownloader downloader;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serve("/poster.jpg", 200, "image/jpeg", JPEG);
        serve("/logo.png", 200, "image/png; charset=binary", new byte[] {1, 2});
        serve("/page.html", 200, "text/html", "<html/>".getBytes());
        serve("/missing.jpg", 404, "text/plain", "weg".getBytes());
        serve("/huge.jpg", 200, "image/jpeg", new byte[2048]);
        server.start();
        downloader = new ImageDownloader(HttpClient.newHttpClient(),
                new ArtworkProperties(DataSize.ofKilobytes(1)));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void downloadsJpeg() {
        DownloadedImage image = downloader.download(url("/poster.jpg"));

        assertThat(image.content()).isEqualTo(JPEG);
        assertThat(image.extension()).isEqualTo("jpg");
    }

    @Test
    void downloadsPngIgnoringContentTypeParameters() {
        assertThat(downloader.download(url("/logo.png")).extension()).isEqualTo("png");
    }

    @Test
    void rejectsOtherContent() {
        assertThatThrownBy(() -> downloader.download(url("/page.html")))
                .isInstanceOfSatisfying(ArtworkProblemException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
                    assertThat(exception.getBody().getDetail()).contains("text/html");
                });
    }

    @Test
    void rejectsTooLargeImage() {
        assertThatThrownBy(() -> downloader.download(url("/huge.jpg")))
                .isInstanceOfSatisfying(ArtworkProblemException.class,
                        exception -> assertThat(exception.getBody().getTitle()).isEqualTo("Bild zu groß"));
    }

    @Test
    void reportsHttpErrorsAsBadGateway() {
        assertThatThrownBy(() -> downloader.download(url("/missing.jpg")))
                .isInstanceOfSatisfying(ArtworkProblemException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(exception.getBody().getDetail()).contains("HTTP 404");
                });
    }

    @Test
    void reportsUnreachableHost() {
        URI unreachable = url("/poster.jpg");
        server.stop(0);

        assertThatThrownBy(() -> downloader.download(unreachable))
                .isInstanceOfSatisfying(ArtworkProblemException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @ParameterizedTest
    @ValueSource(strings = {"file:///etc/passwd", "ftp://example.org/poster.jpg", "poster.jpg"})
    void rejectsOtherSchemes(String url) {
        assertThatThrownBy(() -> downloader.download(URI.create(url)))
                .isInstanceOfSatisfying(ArtworkProblemException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private void serve(String path, int status, String contentType, byte[] body) {
        server.createContext(path, exchange -> {
            exchange.getResponseHeaders().add("Content-Type", contentType);
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
    }

    private URI url(String path) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
    }
}
