package de.tiktak.kmm.artwork;

import java.util.List;
import java.util.function.Consumer;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.plugin.api.ImageType;
import lombok.extern.slf4j.Slf4j;

/**
 * When fetching images automatically, saves the first match that can be downloaded.
 */
@Slf4j
final class ArtworkDownloads {

    /** Attempts per image type in case downloads fail. */
    static final int MAX_DOWNLOAD_ATTEMPTS = 3;

    private ArtworkDownloads() {
    }

    /**
     * A write error in the target folder is not caught, because further attempts would not change it.
     *
     * @return whether an image was saved; the last download error ends up in {@code warnings}
     */
    static boolean saveFirstLoadable(ImageDownloader downloader, ImageType type, List<ArtworkCandidate> candidates,
            Consumer<DownloadedImage> save, List<LocalizedText> warnings) {
        LocalizedText lastProblem = null;
        for (ArtworkCandidate candidate : candidates.stream().limit(MAX_DOWNLOAD_ATTEMPTS).toList()) {
            try {
                save.accept(downloader.download(candidate.url()));
                return true;
            } catch (ImageDownloadException e) {
                log.warn("Could not download {} from {}: {}", type, candidate.url(), e.getMessage());
                lastProblem = e.localizedText();
            }
        }
        if (lastProblem != null) {
            warnings.add(LocalizedText.of("artwork.warning.download").with("type", type).with("reason", lastProblem));
        }
        return false;
    }
}
