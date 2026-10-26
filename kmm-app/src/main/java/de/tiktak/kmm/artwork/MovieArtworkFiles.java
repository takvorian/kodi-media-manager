package de.tiktak.kmm.artwork;

import java.util.Optional;

import org.springframework.stereotype.Component;

import de.tiktak.kmm.plugin.api.ImageType;
import de.tiktak.kmm.scanner.CompanionFiles;
import lombok.RequiredArgsConstructor;

/**
 * Bilddateien eines Films nach Kodi-Konvention ({@code <film>-poster.jpg}, bei DVD/Blu-ray
 * {@code poster.jpg} im Filmordner).
 */
@Component
@RequiredArgsConstructor
class MovieArtworkFiles {

    private final ArtworkFiles artworkFiles;

    Optional<ArtworkFile> find(CompanionFiles files, ImageType type) {
        return artworkFiles.find(slot(files, type));
    }

    ArtworkFile save(CompanionFiles files, ImageType type, DownloadedImage image) {
        return artworkFiles.save(slot(files, type), image);
    }

    void delete(CompanionFiles files, ImageType type) {
        artworkFiles.delete(slot(files, type));
    }

    private static ArtworkSlot slot(CompanionFiles files, ImageType type) {
        return new ArtworkSlot(type, files.artworkCandidates(ArtworkTypes.kodiName(type)));
    }
}
