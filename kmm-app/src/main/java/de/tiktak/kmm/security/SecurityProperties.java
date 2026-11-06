package de.tiktak.kmm.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param enabled {@code false} disables login. Only sensible if KMM is bound exclusively to
 *                {@code localhost} (standalone operation).
 */
@ConfigurationProperties("kmm.security")
public record SecurityProperties(boolean enabled) {
}
