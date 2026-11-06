package de.tiktak.kmm.artwork;

import java.util.List;

import de.tiktak.kmm.i18n.LocalizedText;

/**
 * Takes over warnings of other modules: translatable texts as they are, plain strings as literal texts.
 */
final class ArtworkWarnings {

    private ArtworkWarnings() {
    }

    static List<LocalizedText> of(List<?> warnings) {
        return warnings.stream()
                .map(warning -> warning instanceof LocalizedText text ? text
                        : LocalizedText.literal(String.valueOf(warning)))
                .toList();
    }
}
