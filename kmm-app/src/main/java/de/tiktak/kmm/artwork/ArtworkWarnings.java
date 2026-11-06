package de.tiktak.kmm.artwork;

import java.util.List;

import de.tiktak.kmm.i18n.LocalizedText;

/**
 * Takes over warnings of other modules and plugins: translatable texts as they are, plain strings as literal texts.
 */
final class ArtworkWarnings {

    private ArtworkWarnings() {
    }

    static List<LocalizedText> of(List<?> warnings) {
        return warnings.stream().map(ArtworkWarnings::of).toList();
    }

    static LocalizedText of(Object warning) {
        return warning instanceof LocalizedText text ? text : LocalizedText.literal(String.valueOf(warning));
    }
}
