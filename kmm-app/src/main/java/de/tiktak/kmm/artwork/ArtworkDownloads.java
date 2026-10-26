package de.tiktak.kmm.artwork;

import java.util.List;
import java.util.function.Consumer;

import de.tiktak.kmm.plugin.api.ImageType;
import lombok.extern.slf4j.Slf4j;

/**
 * Speichert beim automatischen Holen von Bildern den ersten Treffer, der sich laden lässt.
 */
@Slf4j
final class ArtworkDownloads {

    /** Versuche je Bildart, falls Downloads fehlschlagen. */
    static final int MAX_DOWNLOAD_ATTEMPTS = 3;

    private ArtworkDownloads() {
    }

    /**
     * Ein Schreibfehler im Zielordner wird nicht abgefangen, weil weitere Versuche daran nichts ändern.
     *
     * @return ob ein Bild gespeichert wurde; der letzte Downloadfehler landet in {@code warnings}
     */
    static boolean saveFirstLoadable(ImageDownloader downloader, ImageType type, List<ArtworkCandidate> candidates,
            Consumer<DownloadedImage> save, List<String> warnings) {
        String lastProblem = null;
        for (ArtworkCandidate candidate : candidates.stream().limit(MAX_DOWNLOAD_ATTEMPTS).toList()) {
            try {
                save.accept(downloader.download(candidate.url()));
                return true;
            } catch (ImageDownloadException e) {
                log.warn("Could not download {} from {}: {}", type, candidate.url(), e.getMessage());
                lastProblem = e.getMessage();
            }
        }
        if (lastProblem != null) {
            warnings.add("%s: %s".formatted(type, lastProblem));
        }
        return false;
    }
}
