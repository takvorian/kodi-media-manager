package de.tiktak.kmm.artwork;

import java.util.List;

import de.tiktak.kmm.i18n.LocalizedText;

/**
 * Result of “fill missing images” for a movie.
 *
 * @param saved    number of newly saved images
 * @param warnings hints about providers or images that could not be loaded
 */
public record ArtworkFillResult(int saved, List<LocalizedText> warnings) {

    public ArtworkFillResult {
        warnings = List.copyOf(warnings);
    }
}
