package de.tiktak.kmm.artwork;

import java.util.Optional;

import org.springframework.stereotype.Component;

import de.tiktak.kmm.plugin.api.ImageType;
import de.tiktak.kmm.scanner.CompanionFiles;
import lombok.RequiredArgsConstructor;

/**
 * Image files of a movie following the Kodi convention ({@code <film>-poster.jpg}, for DVD/Blu-ray
 * {@code poster.jpg} in the movie folder).
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
