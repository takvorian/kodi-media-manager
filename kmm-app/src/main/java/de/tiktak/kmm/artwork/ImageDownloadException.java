package de.tiktak.kmm.artwork;

import java.net.URI;

import org.springframework.http.HttpStatus;

/**
 * Ein Bild kann nicht heruntergeladen werden. Anders als ein Schreibfehler im Filmordner lohnt sich
 * hier ein Versuch mit dem nächsten Treffer.
 */
public class ImageDownloadException extends ArtworkProblemException {

    private static final long BYTES_PER_MEGABYTE = 1024L * 1024L;

    private ImageDownloadException(HttpStatus status, String title, String detail, Throwable cause) {
        super(status, title, detail, cause);
    }

    @Override
    public String getMessage() {
        return getBody().getDetail();
    }

    static ImageDownloadException invalidUrl(String url) {
        return new ImageDownloadException(HttpStatus.BAD_REQUEST, "Ungültige Bildadresse",
                "'%s' ist keine gültige http(s)-Adresse.".formatted(url), null);
    }

    static ImageDownloadException downloadFailed(URI url, String reason, Throwable cause) {
        return new ImageDownloadException(HttpStatus.BAD_GATEWAY, "Bild konnte nicht geladen werden",
                "%s: %s".formatted(url, reason), cause);
    }

    static ImageDownloadException notAnImage(URI url, String contentType) {
        return new ImageDownloadException(HttpStatus.UNPROCESSABLE_CONTENT, "Kein unterstütztes Bild",
                "%s liefert '%s' statt JPEG oder PNG.".formatted(url, contentType), null);
    }

    static ImageDownloadException tooLarge(URI url, long maxBytes) {
        return new ImageDownloadException(HttpStatus.UNPROCESSABLE_CONTENT, "Bild zu groß",
                "%s ist größer als %d MB.".formatted(url, maxBytes / BYTES_PER_MEGABYTE), null);
    }
}
