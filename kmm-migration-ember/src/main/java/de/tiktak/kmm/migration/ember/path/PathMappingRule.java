package de.tiktak.kmm.migration.ember.path;

/**
 * Ersetzt einen Pfad-Präfix aus Ember durch einen Präfix im Zielsystem, z. B.
 * {@code D:\Filme -> /media/filme}.
 */
public record PathMappingRule(String sourcePrefix, String targetPrefix) {

    public PathMappingRule {
        requireNotBlank(sourcePrefix, "sourcePrefix");
        requireNotBlank(targetPrefix, "targetPrefix");
    }

    private static void requireNotBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
