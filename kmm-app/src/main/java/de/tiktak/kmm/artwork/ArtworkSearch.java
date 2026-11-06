package de.tiktak.kmm.artwork;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.plugin.api.ImageType;

/**
 * Matches of an image search over all image types.
 *
 * @param candidatesByType per image type the matches in the order of choice
 * @param warnings         hints about scrapers that are not set up or reported an error
 */
record ArtworkSearch(Map<ImageType, List<ArtworkCandidate>> candidatesByType, List<LocalizedText> warnings) {

    ArtworkSearch {
        Map<ImageType, List<ArtworkCandidate>> copy = new EnumMap<>(ImageType.class);
        candidatesByType.forEach((type, candidates) -> copy.put(type, List.copyOf(candidates)));
        candidatesByType = Collections.unmodifiableMap(copy);
        warnings = List.copyOf(warnings);
    }

    /** Matches of an image type for the whole medium (without season images). */
    List<ArtworkCandidate> candidatesOf(ImageType type) {
        return candidatesOf(type, null);
    }

    /**
     * @param season season, or {@code null} for images of the whole medium
     */
    List<ArtworkCandidate> candidatesOf(ImageType type, Integer season) {
        return candidatesByType.getOrDefault(type, List.of()).stream()
                .filter(candidate -> Objects.equals(candidate.season(), season))
                .toList();
    }
}
