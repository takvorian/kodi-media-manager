package de.tiktak.kmm.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param enabled {@code false} schaltet die Anmeldung ab. Nur sinnvoll, wenn KMM ausschließlich an
 *                {@code localhost} gebunden ist (Standalone-Betrieb).
 */
@ConfigurationProperties("kmm.security")
public record SecurityProperties(boolean enabled) {
}
