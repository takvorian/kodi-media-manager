package de.tiktak.kmm.migration.ember.settings;

/**
 * Proxy configuration from Ember. The password is deliberately not imported because Ember does not store it
 * portably.
 *
 * @param username user name, or {@code null} if the proxy works without login
 */
public record EmberProxySettings(String uri, int port, String username) {
}
