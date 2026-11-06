package de.tiktak.kmm.artwork;

import java.net.URI;

import org.springframework.http.HttpStatus;

import de.tiktak.kmm.i18n.LocalizedText;

/**
 * An image cannot be downloaded. Unlike a write failure in the movie folder, trying the next match is worthwhile
 * here.
 */
public class ImageDownloadException extends ArtworkProblemException {

    private static final long BYTES_PER_MEGABYTE = 1024L * 1024L;

    private ImageDownloadException(HttpStatus status, String key, LocalizedText detail, Throwable cause) {
        super(status, LocalizedText.of(key + ".title"), detail, cause);
    }

    @Override
    public String getMessage() {
        return getBody().getDetail();
    }

    static ImageDownloadException invalidUrl(String url) {
        return new ImageDownloadException(HttpStatus.BAD_REQUEST, "artwork.download.invalidUrl",
                LocalizedText.of("artwork.download.invalidUrl.detail").with("url", url), null);
    }

    static ImageDownloadException downloadFailed(URI url, LocalizedText reason, Throwable cause) {
        return new ImageDownloadException(HttpStatus.BAD_GATEWAY, "artwork.download.failed",
                LocalizedText.of("artwork.download.failed.detail").with("url", url).with("reason", reason), cause);
    }

    static ImageDownloadException notAnImage(URI url, LocalizedText contentType) {
        return new ImageDownloadException(HttpStatus.UNPROCESSABLE_CONTENT, "artwork.download.notAnImage",
                LocalizedText.of("artwork.download.notAnImage.detail")
                        .with("url", url)
                        .with("contentType", contentType), null);
    }

    static ImageDownloadException tooLarge(URI url, long maxBytes) {
        return new ImageDownloadException(HttpStatus.UNPROCESSABLE_CONTENT, "artwork.download.tooLarge",
                LocalizedText.of("artwork.download.tooLarge.detail")
                        .with("url", url)
                        .with("megabytes", maxBytes / BYTES_PER_MEGABYTE), null);
    }
}
