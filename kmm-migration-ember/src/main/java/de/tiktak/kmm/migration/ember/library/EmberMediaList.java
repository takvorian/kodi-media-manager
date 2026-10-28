package de.tiktak.kmm.migration.ember.library;

/**
 * Eine eigene Filmliste aus Embers Filter-Editor, gespeichert als SQLite-View {@code movie-<Name>}.
 *
 * @param name Name ohne Präfix, z. B. {@code tiktak_unviewed}
 * @param sql  die vollständige View-Definition, z. B. {@code CREATE VIEW … AS SELECT * FROM movielist WHERE …}
 */
public record EmberMediaList(String name, String sql) {
}
