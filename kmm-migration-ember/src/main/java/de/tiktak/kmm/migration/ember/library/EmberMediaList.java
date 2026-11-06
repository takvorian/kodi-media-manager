package de.tiktak.kmm.migration.ember.library;

/**
 * A custom movie list from Ember's filter editor, stored as SQLite view {@code movie-<Name>}.
 *
 * @param name name without prefix, e.g. {@code tiktak_unviewed}
 * @param sql  the complete view definition, e.g. {@code CREATE VIEW … AS SELECT * FROM movielist WHERE …}
 */
public record EmberMediaList(String name, String sql) {
}
