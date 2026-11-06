package de.tiktak.kmm.artwork;

import java.util.List;

import de.tiktak.kmm.i18n.LocalizedText;

/**
 * Result of an image search.
 *
 * @param warnings hints about scrapers that are not set up or reported an error
 */
public record ArtworkCandidates(List<ArtworkCandidate> candidates, List<LocalizedText> warnings) {

    public ArtworkCandidates {
        candidates = List.copyOf(candidates);
        warnings = List.copyOf(warnings);
    }
}
