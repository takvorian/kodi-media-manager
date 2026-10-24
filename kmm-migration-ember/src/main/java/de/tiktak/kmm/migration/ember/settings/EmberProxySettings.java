package de.tiktak.kmm.migration.ember.settings;

/**
 * Proxy-Konfiguration aus Ember. Das Passwort wird bewusst nicht übernommen, weil Ember es nicht
 * portabel speichert.
 *
 * @param username Benutzername oder {@code null}, wenn der Proxy ohne Anmeldung arbeitet
 */
public record EmberProxySettings(String uri, int port, String username) {
}
