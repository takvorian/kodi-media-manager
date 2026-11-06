package de.tiktak.kmm.migration.ember.path;

/**
 * Replaces a path prefix from Ember with a prefix in the target system, e.g.
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
